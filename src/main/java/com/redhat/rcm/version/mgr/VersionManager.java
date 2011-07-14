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
import org.apache.maven.mae.MAEException;
import org.apache.maven.mae.app.AbstractMAEApplication;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.jdom.MavenJDOMWriter;
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
import com.redhat.rcm.version.mgr.inject.PomInjector;
import com.redhat.rcm.version.mgr.load.ModelLoader;
import com.redhat.rcm.version.mgr.model.Project;
import com.redhat.rcm.version.model.ProjectKey;
import com.redhat.rcm.version.model.VersionlessProjectKey;
import com.redhat.rcm.version.report.Report;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
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
    
    @Requirement( role = PomInjector.class )
    private Map<String, PomInjector> injectors;
    
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
        
        List<Project> projects;
        
        boolean processPomPlugins = session.isProcessPomPlugins();
        session.setProcessPomPlugins( true );
        try
        {
            projects = modelLoader.buildModels( session, pomFiles );
        }
        catch ( VManException e )
        {
            session.addGlobalError( e );
            return result;
        }
        finally
        {
            session.setProcessPomPlugins( processPomPlugins );
        }
        
        if ( projects != null )
        {
            session.setProjects( projects );
        }

        LOGGER.info( "Modifying " + projects.size() + " project(s)..." );
        for ( final Project project : projects )
        {
            LOGGER.info( "Modifying '" + project.getKey() + "'..." );

            boolean changed = false;
            if ( injectors != null )
            {
                for ( Map.Entry<String, PomInjector> entry : injectors.entrySet() )
                {
                    String key = entry.getKey();
                    PomInjector injector = entry.getValue();
                    
                    LOGGER.info( "Injecting POM changes from: '" + key + "'." );
                    changed = changed || injector.injectChanges( project, session );
                }
            }
            
            if ( changed )
            {
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

    @Override
    public String getId()
    {
        return "rh.vmod";
    }

    public String getName()
    {
        return "RedHat POM Version Modifier";
    }
    
}
