/*
 * Copyright (c) 2012 Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see
 * <http://www.gnu.org/licenses>.
 */

package com.redhat.rcm.version.mgr;

import static com.redhat.rcm.version.mgr.mod.ProjectModder.IMPLIED_MODIFICATIONS;
import static com.redhat.rcm.version.util.InputUtils.getIncludedSubpaths;
import static com.redhat.rcm.version.util.PomUtils.writeModifiedPom;
import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.mae.MAEException;
import org.apache.maven.mae.app.AbstractMAEApplication;
import org.apache.maven.mae.boot.embed.MAEEmbedderBuilder;
import org.apache.maven.mae.internal.container.ComponentSelector;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.mae.project.session.SessionInitializer;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.impl.ArtifactResolver;
import org.sonatype.aether.impl.RemoteRepositoryManager;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.config.SessionConfigurator;
import com.redhat.rcm.version.maven.VManModelResolver;
import com.redhat.rcm.version.mgr.capture.MissingInfoCapture;
import com.redhat.rcm.version.mgr.mod.ProjectModder;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.mgr.verify.ProjectVerifier;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.report.Report;
import com.redhat.rcm.version.util.PomPeek;

@Component( role = VersionManager.class )
public class VersionManager
    extends AbstractMAEApplication
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Requirement
    private ModelBuilder modelBuilder;

    @Requirement
    private ArtifactResolver artifactResolver;

    @Requirement
    private RemoteRepositoryManager remoteRepositoryManager;

    @Requirement( role = Report.class )
    private Map<String, Report> reports;

    @Requirement( role = ProjectModder.class )
    private Map<String, ProjectModder> modders;

    @Requirement( role = ProjectVerifier.class )
    private Map<String, ProjectVerifier> verifiers;

    @Requirement
    private MissingInfoCapture capturer;

    @Requirement
    private SessionConfigurator sessionConfigurator;

    private DefaultModelBuildingRequest baseMbr;

    private static boolean useClasspathScanning = false;

    private static Object lock = new Object();

    private static VersionManager instance;

    public static VersionManager getInstance()
        throws MAEException
    {
        synchronized ( lock )
        {
            if ( instance == null )
            {
                instance = new VersionManager();
                instance.load();
            }
        }

        return instance;
    }

    public void generateReports( final File reportsDir, final VersionManagerSession sessionData )
    {
        if ( reports != null )
        {
            final Set<String> ids = new HashSet<String>();
            for ( final Map.Entry<String, Report> entry : reports.entrySet() )
            {
                final String id = entry.getKey();
                final Report report = entry.getValue();

                if ( !id.endsWith( "_" ) )
                {
                    try
                    {
                        ids.add( id );
                        report.generate( reportsDir, sessionData );
                    }
                    catch ( final VManException e )
                    {
                        logger.error( "Failed to generate report: " + id, e );
                    }
                }
            }

            logger.info( "Wrote reports: [" + StringUtils.join( ids.iterator(), ", " ) + "] to:\n\t" + reportsDir );
        }
    }

    public Set<File> modifyVersions( final File dir, final String pomNamePattern, final String pomExcludePattern, final List<String> boms,
                                     final String toolchain, final VersionManagerSession session )
        throws VManException
    {
        final String[] includedSubpaths = getIncludedSubpaths( dir, pomNamePattern, pomExcludePattern, session );
        final List<File> pomFiles = new ArrayList<File>();

        for ( final String subpath : includedSubpaths )
        {
            File pom = new File( dir, subpath );
            try
            {
                pom = pom.getCanonicalFile();
            }
            catch ( final IOException e )
            {
                pom = pom.getAbsoluteFile();
            }

            if ( !pomFiles.contains( pom ) )
            {
                logger.info( "Loading POM: '" + pom + "'" );
                pomFiles.add( pom );
            }
        }

        final File[] pomFileArray = pomFiles.toArray( new File[] {} );

        configureSession( boms, toolchain, session, pomFileArray );

        final Set<File> outFiles = modVersions( dir, session, session.isPreserveFiles(), pomFileArray );

        logger.info( "Modified " + outFiles.size() + " POM versions in directory.\n\n\tDirectory: " + dir + "\n\tBOMs:\t"
            + StringUtils.join( boms.iterator(), "\n\t\t" ) + "\n\tPOM Backups: " + session.getBackups() + "\n\n" );

        return outFiles;
    }

    public Set<File> modifyVersions( File pom, final List<String> boms, final String toolchain, final VersionManagerSession session )
        throws VManException
    {
        try
        {
            pom = pom.getCanonicalFile();
        }
        catch ( final IOException e )
        {
            pom = pom.getAbsoluteFile();
        }

        configureSession( boms, toolchain, session, pom );

        final Set<File> result = modVersions( pom.getParentFile(), session, true, pom );
        if ( !result.isEmpty() )
        {
            final File out = result.iterator()
                                   .next();

            logger.info( "Modified POM versions.\n\n\tTop POM: " + out + "\n\tBOMs:\t"
                + ( boms == null ? "-NONE-" : StringUtils.join( boms.iterator(), "\n\t\t" ) ) + "\n\tPOM Backups: " + session.getBackups() + "\n\n" );
        }

        return result;
    }

    public void configureSession( final List<String> boms, final String toolchain, final VersionManagerSession session, final File... pomFiles )
        throws VManException
    {
        sessionConfigurator.configureSession( boms, toolchain, session, pomFiles );

        final List<Throwable> errors = session.getErrors();
        if ( errors != null && !errors.isEmpty() )
        {
            throw new MultiVManException( "Failed to configure session.", errors );
        }
    }

    protected LinkedHashSet<Project> loadProjectWithModules( final File topPom, final VersionManagerSession session )
        throws ProjectToolsException, IOException
    {
        final List<PomPeek> peeked = peekAtPomHierarchy( topPom, session );
        final LinkedHashSet<Project> projects = new LinkedHashSet<Project>();

        for ( final PomPeek peek : peeked )
        {
            final File pom = peek.getPom();

            // Sucks, but we have to brute-force reading in the raw model.
            // The effective-model building, below, has a tantalizing getRawModel()
            // method on the result, BUT this seems to return models that have
            // the plugin versions set inside profiles...so they're not entirely
            // raw.
            Model raw = null;
            InputStream in = null;
            try
            {
                in = new FileInputStream( pom );
                raw = new MavenXpp3Reader().read( in );
            }
            catch ( final IOException e )
            {
                session.addError( new VManException( "Failed to build model for POM: %s.\n--> %s", e, pom, e.getMessage() ) );
            }
            catch ( final XmlPullParserException e )
            {
                session.addError( new VManException( "Failed to build model for POM: %s.\n--> %s", e, pom, e.getMessage() ) );
            }
            finally
            {
                closeQuietly( in );
            }

            if ( raw == null )
            {
                continue;
            }

            final Project project;
            if ( session.isUseEffectivePoms() )
            {
                // FIXME: Need an option to disable this for self-contained use cases...
                //    Is this the same as 'non-strict' mode??
                final ModelBuildingRequest req = newModelBuildingRequest( pom, session );
                ModelBuildingResult mbResult = null;
                try
                {
                    mbResult = modelBuilder.build( req );
                }
                catch ( final ModelBuildingException e )
                {
                    session.addError( new VManException( "Failed to build model for POM: %s.\n--> %s", e, pom, e.getMessage() ) );
                }

                if ( mbResult == null )
                {
                    continue;
                }

                project = new Project( raw, mbResult, pom );
            }
            else
            {
                project = new Project( pom, raw );
            }

            projects.add( project );
        }

        return projects;
    }

    protected List<PomPeek> peekAtPomHierarchy( final File topPom, final VersionManagerSession session )
        throws IOException
    {
        final LinkedList<File> pendingPoms = new LinkedList<File>();
        pendingPoms.add( topPom.getCanonicalFile() );

        final String topDir = topPom.getParentFile()
                                    .getCanonicalPath();

        final Set<File> seen = new HashSet<File>();
        final List<PomPeek> peeked = new ArrayList<PomPeek>();

        while ( !pendingPoms.isEmpty() )
        {
            final File pom = pendingPoms.removeFirst();
            seen.add( pom );

            logger.info( "PEEK: " + pom );

            final PomPeek peek = new PomPeek( pom );
            final FullProjectKey key = peek.getKey();
            if ( key != null )
            {
                session.addPeekPom( key, pom );
                peeked.add( peek );

                final File dir = pom.getParentFile();

                final String relPath = peek.getParentRelativePath();
                if ( relPath != null )
                {
                    logger.info( "Found parent relativePath: " + relPath + " in pom: " + pom );
                    File parent = new File( dir, relPath );
                    if ( parent.isDirectory() )
                    {
                        parent = new File( parent, "pom.xml" );
                    }

                    logger.info( "Looking for parent POM: " + parent );

                    parent = parent.getCanonicalFile();
                    if ( parent.getParentFile()
                               .getCanonicalPath()
                               .startsWith( topDir ) && parent.exists() && !seen.contains( parent ) && !pendingPoms.contains( parent ) )
                    {
                        pendingPoms.add( parent );
                    }
                    else
                    {
                        logger.info( "Skipping reference to non-existent parent relativePath: '" + relPath + "' in: " + pom );
                    }
                }

                final Set<String> modules = peek.getModules();
                if ( modules != null && !modules.isEmpty() )
                {
                    for ( final String module : modules )
                    {
                        logger.info( "Found module: " + module + " in pom: " + pom );
                        File modPom = new File( dir, module );
                        if ( modPom.isDirectory() )
                        {
                            modPom = new File( modPom, "pom.xml" );
                        }

                        logger.info( "Looking for module POM: " + modPom );

                        if ( modPom.getParentFile()
                                   .getCanonicalPath()
                                   .startsWith( topDir ) && modPom.exists() && !seen.contains( modPom ) && !pendingPoms.contains( modPom ) )
                        {
                            pendingPoms.addLast( modPom );
                        }
                        else
                        {
                            logger.info( "Skipping reference to non-existent module: '" + module + "' in: " + pom );
                        }
                    }
                }
            }
            else
            {
                logger.info( "Skipping " + pom + " as its a template file." );
            }
        }

        return peeked;
    }

    private synchronized ModelBuildingRequest newModelBuildingRequest( final File pom, final VersionManagerSession session )
    {
        if ( baseMbr == null )
        {
            final DefaultModelBuildingRequest mbr = new DefaultModelBuildingRequest();
            mbr.setSystemProperties( System.getProperties() );
            mbr.setValidationLevel( ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL );
            mbr.setProcessPlugins( false );
            mbr.setLocationTracking( true );

            this.baseMbr = mbr;
        }

        final DefaultModelBuildingRequest req = new DefaultModelBuildingRequest( baseMbr );
        req.setModelSource( new FileModelSource( pom ) );

        final VManModelResolver resolver = new VManModelResolver( session, pom, artifactResolver, remoteRepositoryManager );

        req.setModelResolver( resolver );

        return req;
    }

    private Set<File> modVersions( final File basedir, final VersionManagerSession session, final boolean preserveDirs, final File... pomFiles )
    {
        final Set<File> result = new LinkedHashSet<File>();

        final Set<Project> projects = new HashSet<Project>();
        for ( final File pom : pomFiles )
        {
            if ( session.isExcludedModulePom( pom ) )
            {
                logger.info( "Skipping excluded module pom: {}.", pom );
                continue;
            }

            try
            {
                final Set<Project> pomProjects = loadProjectWithModules( pom, session );
                if ( pomProjects != null && !pomProjects.isEmpty() )
                {
                    projects.addAll( pomProjects );
                }
            }
            catch ( final ProjectToolsException e )
            {
                session.addError( e );
            }
            catch ( final IOException e )
            {
                session.addError( e );
            }
        }

        if ( !session.getErrors()
                     .isEmpty() )
        {
            return result;
        }

        session.setCurrentProjects( projects );

        logger.info( "Modifying " + projects.size() + " project(s)..." );

        // NOTE: Using sorted projects list from session instead of unsorted set from above.
        for ( final Project project : session.getCurrentProjects() )
        {
            logger.info( "Modifying '" + project.getKey() + "'..." );

            List<String> modderKeys = session.getModderKeys();
            modderKeys = calculateActualModderKeys( modderKeys );
            Collections.sort( modderKeys, ProjectModder.KEY_COMPARATOR );

            boolean changed = false;
            if ( modders != null )
            {
                for ( final String key : modderKeys )
                {
                    final ProjectModder modder = modders.get( key );
                    if ( modder == null )
                    {
                        logger.info( "Skipping missing project modifier: '" + key + "'" );
                        session.addError( new VManException( "Cannot find modder for key: '%s'. Skipping...", key ) );
                        continue;
                    }

                    logger.info( "Modifying '" + project.getKey() + " using: '" + key + "' with modder " + modder.getClass()
                                                                                                                 .getName() );
                    changed = modder.inject( project, session ) || changed;
                }
            }

            if ( changed )
            {
                for ( final String key : modderKeys )
                {
                    final ProjectVerifier verifier = verifiers.get( key );
                    if ( verifier != null )
                    {
                        logger.info( "Verifying '" + project.getKey() + "' (" + key + ") with verifier " + verifier.getClass()
                                                                                                                   .getName() );
                        verifier.verify( project, session );
                    }
                }

                logger.info( "Writing modified '" + project.getKey() + "'..." );

                final Model model = project.getModel();
                final Parent parent = model.getParent();

                String groupId = model.getGroupId();
                String originalVersion = model.getVersion();
                if ( parent != null )
                {
                    if ( groupId == null )
                    {
                        groupId = parent.getGroupId();
                    }

                    if ( originalVersion == null )
                    {
                        originalVersion = parent.getVersion();
                    }
                }

                final ProjectKey originalCoord = new VersionlessProjectKey( groupId, model.getArtifactId() );
                final File pom = project.getPom();

                final File out = writePom( model, originalCoord, originalVersion, pom, basedir, session, preserveDirs );
                if ( out != null )
                {
                    result.add( out );
                }
            }
            else
            {
                logger.info( project.getKey() + " NOT modified." );
            }
        }

        if ( session.getCapturePom() != null )
        {
            capturer.captureMissing( session );

            logger.warn( "\n\n\n\nMissing version information has been logged to:\n\n\t" + session.getCapturePom()
                                                                                                  .getAbsolutePath() + "\n\n\n\n" );
        }

        return result;
    }

    private List<String> calculateActualModderKeys( final List<String> modders )
    {
        final List<String> keys = new ArrayList<String>( modders );
        for ( final String key : modders )
        {
            final Set<String> implications = IMPLIED_MODIFICATIONS.get( key );
            if ( implications != null )
            {
                final int idx = keys.indexOf( key );
                for ( final String implied : implications )
                {
                    if ( !keys.contains( implied ) )
                    {
                        keys.add( idx, implied );
                    }
                }
            }
        }

        return keys;
    }

    private File writePom( final Model model, final ProjectKey originalCoord, final String originalVersion, final File pom, final File basedir,
                           final VersionManagerSession session, final boolean preserveDirs )
    {
        File backup = pom;

        final File backupDir = session.getBackups();
        if ( backupDir != null )
        {
            String path = pom.getParent();
            path = path.substring( basedir.getPath()
                                          .length() );

            final File dir = new File( backupDir, path );
            if ( !dir.exists() && !dir.mkdirs() )
            {
                session.addError( new VManException( "Failed to create backup subdirectory: %s", dir ) );
                return null;
            }

            backup = new File( dir, pom.getName() );
            try
            {
                session.getLog( pom )
                       .add( "Writing: %s\nTo backup: %s", pom, backup );
                FileUtils.copyFile( pom, backup );
            }
            catch ( final IOException e )
            {
                session.addError( new VManException( "Error making backup of POM: %s.\n\tTarget: %s\n\tReason: %s", e, pom, backup, e.getMessage() ) );
                return null;
            }
        }

        String version = model.getVersion();
        String groupId = model.getGroupId();
        if ( model.getParent() != null )
        {
            final Parent parent = model.getParent();
            if ( version == null )
            {
                version = parent.getVersion();
            }

            if ( groupId == null )
            {
                groupId = parent.getGroupId();
            }
        }

        boolean relocatePom = false;

        final ProjectKey coord = new VersionlessProjectKey( groupId, model.getArtifactId() );
        if ( !preserveDirs && ( !coord.equals( originalCoord ) || !version.equals( originalVersion ) ) )
        {
            relocatePom = true;
        }

        return writeModifiedPom( model, pom, coord, version, basedir, session, relocatePom );
    }

    @Override
    public String getId()
    {
        return "rh.vmod";
    }

    @Override
    public String getName()
    {
        return "Red Hat POM Version Modifier";
    }

    @Override
    protected void configureBuilder( final MAEEmbedderBuilder builder )
        throws MAEException
    {
        super.configureBuilder( builder );
        if ( useClasspathScanning )
        {
            builder.withClassScanningEnabled( true );
        }
    }

    public static void setClasspathScanning( final boolean scanning )
    {
        if ( instance == null )
        {
            useClasspathScanning = scanning;
        }
    }

    public Map<String, ProjectModder> getModders()
    {
        return modders;
    }

    public Map<String, Report> getReports()
    {
        return reports;
    }

    @Override
    public ComponentSelector getComponentSelector()
    {
        return new ComponentSelector().setSelection( SessionInitializer.class, "vman" );
    }
}
