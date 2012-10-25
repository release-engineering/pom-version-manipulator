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

import static com.redhat.rcm.version.util.AnnotationUtils.nameOf;
import static com.redhat.rcm.version.util.InputUtils.getIncludedSubpaths;
import static com.redhat.rcm.version.util.PomUtils.writeModifiedPom;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.log4j.Logger;
import org.apache.maven.mae.project.ModelLoader;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
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

@Singleton
public class VersionManager
{

    private static final Logger LOGGER = Logger.getLogger( VersionManager.class );

    @Inject
    private ModelLoader modelLoader;

    @Inject
    private Instance<Report> injectedReports;

    private Set<Report> reports;

    @Inject
    private Instance<ProjectModder> injectedModders;

    private Map<String, ProjectModder> modders;

    @Inject
    private Instance<ProjectVerifier> injectedVerifiers;

    private Map<String, ProjectVerifier> verifiers;

    @Inject
    private MissingInfoCapture capturer;

    @Inject
    private SessionConfigurator sessionConfigurator;

    private HashMap<String, String> pomExcludedModules;

    @PostConstruct
    public void initialize()
    {
        if ( injectedReports != null )
        {
            reports = new HashSet<Report>();
            for ( final Report report : injectedReports )
            {
                reports.add( report );
            }
        }

        if ( injectedModders != null )
        {
            modders = new HashMap<String, ProjectModder>();
            for ( final ProjectModder modder : injectedModders )
            {
                String named = nameOf( modder );
                if ( named == null )
                {
                    throw new IllegalArgumentException( "Required @Named(..) annotation missing for ProjectModder: "
                        + modder.getClass()
                                .getName() );
                }

                if ( named.startsWith( "modder/" ) )
                {
                    named = named.substring( "modder/".length() );
                }

                LOGGER.info( "Adding modder: " + named + " (" + modder.getClass()
                                                                      .getName() + ")" );

                modders.put( named, modder );
            }
        }

        if ( injectedVerifiers != null )
        {
            verifiers = new HashMap<String, ProjectVerifier>();
            for ( final ProjectVerifier verifier : injectedVerifiers )
            {
                String named = nameOf( verifier );
                if ( named == null )
                {
                    throw new IllegalArgumentException( "Required @Named(..) annotation missing for ProjectVerifier: "
                        + verifier.getClass()
                                  .getName() );
                }

                if ( named.startsWith( "verifier/" ) )
                {
                    named = named.substring( "verifier/".length() );
                }

                LOGGER.info( "Adding verifier: " + named + " (" + verifier.getClass()
                                                                          .getName() + ")" );

                verifiers.put( named, verifier );
            }
        }
    }

    public void generateReports( final File reportsDir, final VersionManagerSession sessionData )
    {
        if ( reports != null )
        {
            final Set<String> ids = new HashSet<String>();
            for ( final Report report : reports )
            {
                final String id = nameOf( report );

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
        throws VManException
    {
        configureSession( boms, toolchain, session );

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
        throws VManException
    {
        configureSession( boms, toolchain, session );

        try
        {
            pom = pom.getCanonicalFile();
        }
        catch ( final IOException e )
        {
            pom = pom.getAbsoluteFile();
        }

        final Set<File> result = modVersions( pom.getParentFile(), session, true, pom );
        if ( !result.isEmpty() )
        {
            final File out = result.iterator()
                                   .next();

            LOGGER.info( "Modified POM versions.\n\n\tTop POM: " + out + "\n\tBOMs:\t"
                + ( boms == null ? "-NONE-" : StringUtils.join( boms.iterator(), "\n\t\t" ) ) + "\n\tPOM Backups: "
                + session.getBackups() + "\n\n" );
        }

        return result;
    }

    public void configureSession( final List<String> boms, final String toolchain, final VersionManagerSession session )
        throws VManException
    {
        sessionConfigurator.configureSession( boms, toolchain, session );

        final List<Throwable> errors = session.getErrors();
        if ( errors != null && !errors.isEmpty() )
        {
            throw new MultiVManException( "Failed to configure session.", errors );
        }
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
                    groupId = ( m.getGroupId() == null ? m.getParent()
                                                          .getGroupId() : m.getGroupId() );
                }

                final String v = pomExcludedModules.get( groupId );
                if ( v != null && m.getArtifactId()
                                   .equals( v ) )
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
        // TODO: Projects may need to be sorted to parents-first if we have to use modelBuilder where there are parent-child relationships.
        for ( final Project project : session.getCurrentProjects() )
        {
            LOGGER.info( "Modifying '" + project.getKey() + "'..." );

            final List<String> modderKeys = session.getModderKeys();
            Collections.sort( modderKeys, ProjectModder.KEY_COMPARATOR );

            boolean changed = false;
            if ( modders != null )
            {
                // TODO: This may need to be the outer loop, if we need to deal with parent/child relationships in modelBuilder...
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

            LOGGER.warn( "\n\n\n\nMissing version information has been logged to:\n\n\t" + session.getCapturePom()
                                                                                                  .getAbsolutePath()
                + "\n\n\n\n" );
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
                session.addError( new VManException( "Error making backup of POM: %s.\n\tTarget: %s\n\tReason: %s", e,
                                                     pom, backup, e.getMessage() ) );
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
