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

import static com.redhat.rcm.version.util.PomUtils.writeModifiedPom;

import org.apache.log4j.Logger;
import org.apache.maven.mae.MAEException;
import org.apache.maven.mae.app.AbstractMAEApplication;
import org.apache.maven.mae.boot.embed.MAEEmbedderBuilder;
import org.apache.maven.mae.project.ModelLoader;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.aether.util.DefaultRequestTrace;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.config.SessionConfigurator;
import com.redhat.rcm.version.mgr.capture.MissingInfoCapture;
import com.redhat.rcm.version.mgr.mod.ProjectModder;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.mgr.verify.ProjectVerifier;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.report.Report;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component( role = VersionManager.class )
public class VersionManager
    extends AbstractMAEApplication
{

    private static final Logger LOGGER = Logger.getLogger( VersionManager.class );

    @Requirement
    private ModelLoader modelLoader;

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

    private HashMap<String, String> pomExcludedModules;

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
                        LOGGER.error( "Failed to generate report: " + id, e );
                    }
                }
            }

            LOGGER.info( "Wrote reports: [" + StringUtils.join( ids.iterator(), ", " ) + "] to:\n\t" + reportsDir );
        }
    }

    public Set<File> modifyVersions( final File dir, final String pomNamePattern, final String pomExcludePattern,
                                     final List<String> boms, final String toolchain,
                                     final VersionManagerSession session )
    {
        final DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( dir );

        final String[] initExcludes =
            new String[] { session.getWorkspace().getName() + "/**", session.getReports().getName() + "/**" };

        final String[] excludePattern =
            pomExcludePattern == null ? new String[] {} : pomExcludePattern.split( "\\s*,\\s*" );

        final String[] excludes = Arrays.copyOf( initExcludes, initExcludes.length + excludePattern.length );

        System.arraycopy( excludePattern, 0, excludes, initExcludes.length, excludePattern.length );

        scanner.setExcludes( excludes );
        scanner.addDefaultExcludes();

        final String[] includes = pomNamePattern.split( "\\s*,\\s*" );
        scanner.setIncludes( includes );

        scanner.scan();

        try
        {
            sessionConfigurator.configureSession( boms, toolchain, session );
        }
        catch ( final VManException e )
        {
            session.addError( e );
            return Collections.emptySet();
        }

        final List<File> pomFiles = new ArrayList<File>();
        final String[] includedSubpaths = scanner.getIncludedFiles();

        LOGGER.debug( "Scanning from " + dir + " and got included files " + Arrays.toString( includedSubpaths )
            + " and got excluded files " + Arrays.toString( scanner.getExcludedFiles() ) );

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
                LOGGER.info( "Loading POM: '" + pom + "'" );
                pomFiles.add( pom );
            }
        }

        final Set<File> outFiles =
            modVersions( dir, session, session.isPreserveFiles(), pomFiles.toArray( new File[] {} ) );

        LOGGER.info( "Modified " + outFiles.size() + " POM versions in directory.\n\n\tDirectory: " + dir
            + "\n\tBOMs:\t" + StringUtils.join( boms.iterator(), "\n\t\t" ) + "\n\tPOM Backups: "
            + session.getBackups() + "\n\n" );

        return outFiles;
    }

    public Set<File> modifyVersions( File pom, final List<String> boms, final String toolchain,
                                     final VersionManagerSession session )
    {
        try
        {
            pom = pom.getCanonicalFile();
        }
        catch ( final IOException e )
        {
            pom = pom.getAbsoluteFile();
        }

        try
        {
            sessionConfigurator.configureSession( boms, toolchain, session );
        }
        catch ( final VManException e )
        {
            session.addError( e );
            return Collections.emptySet();
        }

        final Set<File> result = modVersions( pom.getParentFile(), session, true, pom );
        if ( !result.isEmpty() )
        {
            final File out = result.iterator().next();

            LOGGER.info( "Modified POM versions.\n\n\tTop POM: " + out + "\n\tBOMs:\t"
                + ( boms == null ? "-NONE-" : StringUtils.join( boms.iterator(), "\n\t\t" ) ) + "\n\tPOM Backups: "
                + session.getBackups() + "\n\n" );
        }

        return result;
    }

    private Set<File> modVersions( final File basedir, final VersionManagerSession session, final boolean preserveDirs,
                                   final File... pomFiles )
    {
        final Set<File> result = new LinkedHashSet<File>();

        final boolean processPomPlugins = session.isProcessPomPlugins();
        session.setProcessPomPlugins( true );

        List<Model> models;
        try
        {
            models = modelLoader.loadRawModels( session, true, new DefaultRequestTrace( "VMan ROOT" ), pomFiles );
            // projects = modelLoader.buildModels( session, pomFiles );
        }
        catch ( final ProjectToolsException e )
        {
            session.addError( e );
            return result;
        }
        finally
        {
            session.setProcessPomPlugins( processPomPlugins );
        }

        if ( pomExcludedModules != null )
        {
            for ( final Iterator<Model> i = models.iterator(); i.hasNext(); )
            {
                final Model m = i.next();

                String groupId;
                if ( m.getGroupId() == null && m.getParent() == null )
                {
                    LOGGER.warn( "Unable to determine groupId for model " + m );
                    continue;
                }
                else
                {
                    groupId = ( m.getGroupId() == null ? m.getParent().getGroupId() : m.getGroupId() );
                }

                final String v = pomExcludedModules.get( groupId );
                if ( v != null && m.getArtifactId().equals( v ) )
                {
                    i.remove();
                }
            }
        }

        try
        {
            session.setCurrentProjects( models );
        }
        catch ( final ProjectToolsException e )
        {
            LOGGER.info( "Cannot construct project key. Error: " + e.getMessage() );
            session.addError( e );
            return result;
        }

        LOGGER.info( "Modifying " + models.size() + " project(s)..." );
        for ( final Project project : session.getCurrentProjects() )
        {
            LOGGER.info( "Modifying '" + project.getKey() + "'..." );

            final Set<String> modderKeys = session.getModderKeys();

            boolean changed = false;
            if ( modders != null )
            {
                for ( final String key : modderKeys )
                {
                    final ProjectModder modder = modders.get( key );
                    if ( modder == null )
                    {
                        LOGGER.info( "Skipping missing project modifier: '" + key + "'" );
                        session.addError( new VManException( "Cannot find modder for key: '%s'. Skipping...", key ) );
                        continue;
                    }

                    LOGGER.info( "Modifying '" + project.getKey() + " using: '" + key + "'" );
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
                        LOGGER.info( "Verifying '" + project.getKey() + "' (" + key + ")..." );
                        verifier.verify( project, session );
                    }
                }

                LOGGER.info( "Writing modified '" + project.getKey() + "'..." );

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
                LOGGER.info( project.getKey() + " NOT modified." );
            }
        }

        if ( session.getCapturePom() != null )
        {
            capturer.captureMissing( session );

            LOGGER.warn( "\n\n\n\nMissing version information has been logged to:\n\n\t"
                + session.getCapturePom().getAbsolutePath() + "\n\n\n\n" );
        }

        return result;
    }

    private File writePom( final Model model, final ProjectKey originalCoord, final String originalVersion,
                           final File pom, final File basedir, final VersionManagerSession session,
                           final boolean preserveDirs )
    {
        File backup = pom;

        final File backupDir = session.getBackups();
        if ( backupDir != null )
        {
            String path = pom.getParent();
            path = path.substring( basedir.getPath().length() );

            final File dir = new File( backupDir, path );
            if ( !dir.exists() && !dir.mkdirs() )
            {
                session.addError( new VManException( "Failed to create backup subdirectory: %s", dir ) );
                return null;
            }

            backup = new File( dir, pom.getName() );
            try
            {
                session.getLog( pom ).add( "Writing: %s\nTo backup: %s", pom, backup );
                FileUtils.copyFile( pom, backup );
            }
            catch ( final IOException e )
            {
                session.addError( new VManException( "Error making backup of POM: %s.\n\tTarget: %s\n\tReason: %s",
                                                     e,
                                                     pom,
                                                     backup,
                                                     e.getMessage() ) );
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
        return "RedHat POM Version Modifier";
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

    public void setPomExcludeModules( final String pomExcludeModules )
    {
        if ( pomExcludeModules == null )
        {
            return;
        }

        final String[] modules = pomExcludeModules.split( "," );

        pomExcludedModules = new HashMap<String, String>();

        for ( final String m : modules )
        {
            final int index = m.indexOf( ':' );
            pomExcludedModules.put( m.substring( 0, index ), m.substring( index + 1 ) );
        }
    }
}
