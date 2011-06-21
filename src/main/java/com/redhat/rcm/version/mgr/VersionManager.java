/*
 * Copyright (c) 2011 Red Hat, Inc.
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

import static java.io.File.separatorChar;

import org.apache.log4j.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.mae.MAEException;
import org.apache.maven.mae.app.AbstractMAEApplication;
import org.apache.maven.mae.project.ProjectLoader;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.jdom.MavenJDOMWriter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.Format.TextMode;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.config.SessionConfigurator;
import com.redhat.rcm.version.model.FullProjectKey;
import com.redhat.rcm.version.model.ProjectKey;
import com.redhat.rcm.version.model.VersionlessProjectKey;
import com.redhat.rcm.version.report.Report;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
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
    private ProjectLoader projectLoader;

    @Requirement( role = Report.class )
    private Map<String, Report> reports;
    
    @Requirement
    private SessionConfigurator sessionConfigurator;

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

    public Set<File> modifyVersions( final File dir, final String pomNamePattern, final List<String> boms,
                                     final String toolchain, final VersionManagerSession session )
    {
        final DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( dir );

        scanner.setExcludes( new String[] { session.getWorkspace().getName() + "/**",
            session.getReports().getName() + "/**", } );

        scanner.addDefaultExcludes();

        final String[] includes = pomNamePattern.split( "\\s*,\\s*" );
        scanner.setIncludes( includes );

        scanner.scan();

        sessionConfigurator.configureSession( boms, toolchain, session );

        final List<File> pomFiles = new ArrayList<File>();
        final String[] includedSubpaths = scanner.getIncludedFiles();
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

        final Set<File> outFiles = modVersions( dir, session, session.isPreserveFiles(), pomFiles.toArray( new File[]{} ) );

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

        sessionConfigurator.configureSession( boms, toolchain, session );
        
        final Set<File> result = modVersions( pom.getParentFile(), session, true, pom );
        if ( !result.isEmpty() )
        {
            final File out = result.iterator().next();

            LOGGER.info( "Modified POM versions.\n\n\tTop POM: " + out + "\n\tBOMs:\t"
                + StringUtils.join( boms.iterator(), "\n\t\t" ) + "\n\tPOM Backups: " + session.getBackups() + "\n\n" );
        }

        return result;
    }

    private Set<File> modVersions( final File basedir, final VersionManagerSession session,
                                   final boolean preserveDirs, final File...pomFiles )
    {
        final Set<File> result = new LinkedHashSet<File>();
        
        List<MavenProject> projects;
        try
        {
            projects = projectLoader.buildReactorProjectInstances( session, false, pomFiles );
        }
        catch ( ProjectToolsException e )
        {
            session.addGlobalError( e );
            return result;
        }
        
        Map<FullProjectKey, MavenProject> projectMap = new HashMap<FullProjectKey, MavenProject>();
        if ( projects != null )
        {
            for ( MavenProject project : projects )
            {
                projectMap.put( new FullProjectKey( project ), project );
            }
        }

        LOGGER.info( "Modifying " + projects.size() + "..." );
        for ( final MavenProject project : projectMap.values() )
        {
            LOGGER.info( "Modifying '" + project.getId() + "'..." );

            final Model model = project.getOriginalModel();

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

            final File pom = project.getFile();

            boolean changed = modifyCoord( model, pom, session );
            if ( session.isNormalizeBomUsage() )
            {
                LOGGER.info( "Introducing BOMs to '" + project.getId() + "'..." );
                changed = changed || introduceBoms( model, projectMap, pom, session );
            }

            if ( model.getDependencies() != null )
            {
                LOGGER.info( "Processing dependencies for '" + project.getId() + "'..." );
                for ( final Iterator<Dependency> it = model.getDependencies().iterator(); it.hasNext(); )
                {
                    final Dependency dep = it.next();
                    final DepModResult depResult = modifyDep( dep, pom, session, false );
                    if ( depResult == DepModResult.DELETED )
                    {
                        it.remove();
                        changed = true;
                    }
                    else
                    {
                        changed = DepModResult.MODIFIED == depResult || changed;
                    }
                }
            }

            if ( model.getDependencyManagement() != null && model.getDependencyManagement().getDependencies() != null )
            {
                LOGGER.info( "Processing dependencyManagement for '" + project.getId() + "'..." );
                for ( final Iterator<Dependency> it = model.getDependencyManagement().getDependencies().iterator(); it.hasNext(); )
                {
                    final Dependency dep = it.next();
                    final DepModResult depResult = modifyDep( dep, pom, session, true );
                    if ( depResult == DepModResult.DELETED )
                    {
                        it.remove();
                        changed = true;
                    }
                    else
                    {
                        changed = DepModResult.MODIFIED == depResult || changed;
                    }
                }
            }

            if ( changed )
            {
                LOGGER.info( "Writing modified '" + project.getId() + "'..." );

                final File out = writePom( model, originalCoord, originalVersion, pom, basedir, session, preserveDirs );
                if ( out != null )
                {
                    result.add( out );
                }
            }
            else
            {
                LOGGER.info( project.getId() + " NOT modified." );
            }
        }

        return result;
    }

    private boolean introduceBoms( final Model model, final Map<FullProjectKey, MavenProject> projects, final File pom,
                                   final VersionManagerSession session )
    {
        boolean changed = false;

        final Parent parent = model.getParent();
        if ( parent != null )
        {
            final FullProjectKey key = new FullProjectKey( parent );
            if ( projects.containsKey( key ) )
            {
                LOGGER.info( "Skipping BOM introduction for: '" + model.getId() + "'. Will modify parent POM (" + key
                    + ") instead..." );
                return changed;
            }
        }

        final Set<FullProjectKey> boms = new LinkedHashSet<FullProjectKey>( session.getBomCoords() );
        DependencyManagement dm = model.getDependencyManagement();
        if ( dm != null )
        {
            final List<Dependency> deps = dm.getDependencies();
            if ( deps != null )
            {
                for ( final Dependency dep : deps )
                {
                    LOGGER.info( "Checking managed dependency: " + dep );

                    if ( dep.getVersion() != null && Artifact.SCOPE_IMPORT.equals( dep.getScope() )
                        && "pom".equals( dep.getType() ) )
                    {
                        LOGGER.info( "Removing: " + dep + " from: " + pom );
                        final FullProjectKey k = new FullProjectKey( dep );
                        boms.remove( k );
                        changed = true;
                    }
                    // else if ( session.getArtifactVersion( new VersionlessProjectKey( dep ) ) == null )
                    // {
                    // LOGGER.warn( "NOT removing dependency: " + dep + "; no alternatives exist in BOMs." );
                    // }
                }
            }
        }
        else
        {
            LOGGER.info( "Introducing clean dependencyManagement section to contain BOMs..." );
            dm = new DependencyManagement();
            model.setDependencyManagement( dm );
            changed = true;
        }

        for ( final FullProjectKey bk : boms )
        {
            LOGGER.info( "Adding BOM: " + bk + " to: " + pom );
            dm.addDependency( bk.getBomDependency() );
            changed = true;
        }

        return changed;
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
                session.addError( pom, new VManException( "Failed to create backup subdirectory: %s", dir ) );
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
                session.addError( pom,
                                  new VManException( "Error making backup of POM: %s.\n\tTarget: %s\n\tReason: %s",
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

        File out = pom;
        if ( relocatePom )
        {
            final StringBuilder pathBuilder = new StringBuilder();
            pathBuilder.append( coord.getGroupId().replace( '.', separatorChar ) )
                       .append( separatorChar )
                       .append( coord.getArtifactId() )
                       .append( separatorChar )
                       .append( version )
                       .append( separatorChar )
                       .append( coord.getArtifactId() )
                       .append( '-' )
                       .append( version )
                       .append( ".pom" );

            out = new File( basedir, pathBuilder.toString() );
            final File outDir = out.getParentFile();
            outDir.mkdirs();
        }

        Writer writer = null;
        try
        {
            final SAXBuilder builder = new SAXBuilder();
            builder.setIgnoringBoundaryWhitespace( false );
            builder.setIgnoringElementContentWhitespace( false );

            final Document doc = builder.build( pom );

            String encoding = model.getModelEncoding();
            if ( encoding == null )
            {
                encoding = "UTF-8";
            }

            final Format format = Format.getRawFormat().setEncoding( encoding ).setTextMode( TextMode.PRESERVE );

            session.getLog( pom ).add( "Writing modified POM: %s", out );
            writer = WriterFactory.newWriter( out, encoding );

            new MavenJDOMWriter().write( model, doc, writer, format );

            if ( relocatePom && !out.equals( pom ) )
            {
                session.getLog( pom ).add( "Deleting original POM: %s\nPurging unused directories...", pom );
                pom.delete();
                File dir = pom.getParentFile();
                while ( dir != null && !basedir.equals( dir ) )
                {
                    final String[] listing = dir.list();
                    if ( listing == null || listing.length < 1 )
                    {
                        dir.delete();
                        dir = dir.getParentFile();
                    }
                    else
                    {
                        break;
                    }
                }
            }
        }
        catch ( final IOException e )
        {
            session.addError( pom,
                              new VManException( "Failed to write modified POM to: %s\n\tReason: %s",
                                                 e,
                                                 out,
                                                 e.getMessage() ) );
        }
        catch ( final JDOMException e )
        {
            session.addError( pom, new VManException( "Failed to read original POM for rewrite: %s\n\tReason: %s",
                                                      e,
                                                      out,
                                                      e.getMessage() ) );
        }
        finally
        {
            IOUtil.close( writer );
        }

        return out;
    }

    private DepModResult modifyDep( final Dependency dep, final File pom, final VersionManagerSession session,
                                    final boolean isManaged )
    {
        DepModResult result = DepModResult.UNCHANGED;

        final VersionlessProjectKey key = new VersionlessProjectKey( dep.getGroupId(), dep.getArtifactId() );
        final ProjectKey newKey = session.getRelocation( key );
        if ( newKey != null && !key.equals( newKey ) )
        {
            LOGGER.info( "Relocating dependency: " + key + " to: " + newKey );
            dep.setGroupId( newKey.getGroupId() );
            dep.setArtifactId( newKey.getArtifactId() );
        }
        else
        {
            LOGGER.info( "No relocation available for: " + key );
        }

        String version = dep.getVersion();

        if ( version == null )
        {
            session.getLog( pom ).add( "NOT changing version for: %s%s. Version is inherited.",
                                       key,
                                       isManaged ? " [MANAGED]" : "" );
            return result;
        }

        version = session.getArtifactVersion( key );
        if ( version != null )
        {
            if ( !version.equals( dep.getVersion() ) )
            {
                session.getLog( pom ).add( "Changing version for: %s%s.\n\tFrom: %s\n\tTo: %s.",
                                           key,
                                           isManaged ? " [MANAGED]" : "",
                                           dep.getVersion(),
                                           version );

                if ( session.isNormalizeBomUsage() )
                {
                    // wipe this out, and use the one in the BOM implicitly...DRY-style.
                    dep.setVersion( null );
                    if ( isManaged
                        && ( dep.getScope() == null || dep.getExclusions() == null || dep.getExclusions().isEmpty() ) )
                    {
                        result = DepModResult.DELETED;
                    }
                    else
                    {
                        result = DepModResult.MODIFIED;
                    }
                }
                else
                {
                    dep.setVersion( version );
                    result = DepModResult.MODIFIED;
                }
            }
            else if ( session.isNormalizeBomUsage() )
            {
                // wipe this out, and use the one in the BOM implicitly...DRY-style.
                dep.setVersion( null );
                if ( isManaged
                    && ( dep.getScope() == null || dep.getExclusions() == null || dep.getExclusions().isEmpty() ) )
                {
                    result = DepModResult.DELETED;
                }
                else
                {
                    result = DepModResult.MODIFIED;
                }
            }
            else
            {
                session.getLog( pom ).add( "Version for: %s%s is already correct.", key, isManaged ? " [MANAGED]" : "" );
            }
        }
        else
        {
            session.addMissingVersion( pom, key );
        }

        return result;
    }

    private boolean modifyCoord( final Model model, final File pom, final VersionManagerSession session )
    {
        boolean changed = false;
        final Parent parent = model.getParent();

        String groupId = model.getGroupId();
        if ( groupId == null && parent != null )
        {
            groupId = parent.getGroupId();
        }

        if ( model.getVersion() != null )
        {
            final VersionlessProjectKey key = new VersionlessProjectKey( groupId, model.getArtifactId() );
            final ProjectKey newKey = session.getRelocation( key );

            if ( newKey != null && !key.equals( newKey ) )
            {
                if ( groupId == model.getGroupId() )
                {
                    model.setGroupId( newKey.getGroupId() );
                }
                model.setArtifactId( newKey.getArtifactId() );
            }

            final String version = session.getArtifactVersion( key );
            if ( version != null )
            {
                if ( !version.equals( model.getVersion() ) )
                {
                    session.getLog( pom ).add( "Changing POM version from: %s to: %s", model.getVersion(), version );
                    model.setVersion( version );
                    changed = true;
                }
                else
                {
                    session.getLog( pom ).add( "POM (%s) version is correct: %s", key, model.getVersion() );
                }
            }
            else
            {
                session.addMissingVersion( pom, key );
                session.getLog( pom ).add( "POM version is missing in BOM: %s", key );
            }
        }

        if ( parent != null )
        {
            final VersionlessProjectKey key = new VersionlessProjectKey( parent.getGroupId(), parent.getArtifactId() );
            final ProjectKey newKey = session.getRelocation( key );

            if ( newKey != null && !key.equals( newKey ) )
            {
                parent.setGroupId( newKey.getGroupId() );
                parent.setArtifactId( newKey.getArtifactId() );
            }

            final String version = session.getArtifactVersion( key );
            if ( version == null )
            {
                session.addMissingVersion( pom, key );
                session.getLog( pom ).add( "POM parent version is missing in BOM: %s", key );
            }
            else
            {
                if ( !version.equals( parent.getVersion() ) )
                {
                    session.getLog( pom ).add( "Changing POM parent (%s) version\n\tFrom: %s\n\tTo: %s",
                                               key,
                                               parent.getVersion(),
                                               version );
                    parent.setVersion( version );
                    changed = true;
                }
                else
                {
                    session.getLog( pom ).add( "POM parent (%s) version is correct: %s", key, parent.getVersion() );
                }
            }
        }

        return changed;
    }


    @Override
    public String getId()
    {
        return "rh.vmod";
    }

    public String getName()
    {
        return "RedHat POM Version Modifier";
    }

    private static enum DepModResult
    {
        UNCHANGED, MODIFIED, DELETED;
    }

}
