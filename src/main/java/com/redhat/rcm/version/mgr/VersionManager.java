/*
 * Copyright (c) 2010 Red Hat, Inc.
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

import org.apache.log4j.Logger;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.io.jdom.MavenJDOMWriter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest.RepositoryMerging;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.commonjava.emb.EMBException;
import org.commonjava.emb.app.AbstractEMBApplication;
import org.commonjava.emb.boot.embed.EMBEmbeddingException;
import org.commonjava.emb.boot.services.EMBServiceManager;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.Format.TextMode;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.report.Report;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component( role = VersionManager.class )
public class VersionManager
    extends AbstractEMBApplication
{

    private static final Logger LOGGER = Logger.getLogger( VersionManager.class );

    @Requirement
    private ProjectBuilder projectBuilder;

    @Requirement
    private EMBServiceManager serviceManager;

    @Requirement( role = Report.class )
    private Map<String, Report> reports;

    private static Object lock = new Object();

    private static VersionManager instance;

    private VersionManager()
    {
    }

    public static VersionManager getInstance()
        throws EMBException
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

    // public VersionManager( final ModelReader modelReader, final ModelInterpolator modelInterpolator,
    // final Map<String, Report> reports )
    // {
    // this.modelReader = modelReader;
    // this.modelInterpolator = modelInterpolator;
    // this.reports = reports;
    // }

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

    public Set<File> modifyVersions( final File dir, final String pomNamePattern, final List<File> boms,
                                     final VersionManagerSession session )
    {
        final DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( dir );
        scanner.addDefaultExcludes();
        scanner.setIncludes( new String[] { pomNamePattern } );

        scanner.scan();

        mapBOMDependencyManagement( boms, session );

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
                pomFiles.add( pom );
            }
        }

        final Set<File> outFiles = modVersions( pomFiles, dir, session, session.isPreserveDirs() );

        LOGGER.info( "Modified " + outFiles.size() + " POM versions in directory.\n\n\tDirectory: " + dir
                        + "\n\tBOMs:\t" + StringUtils.join( boms.iterator(), "\n\t\t" ) + "\n\tPOM Backups: "
                        + session.getBackups() + "\n\n" );

        return outFiles;
    }

    public File modifyVersions( File pom, final List<File> boms, final VersionManagerSession session )
    {
        try
        {
            pom = pom.getCanonicalFile();
        }
        catch ( final IOException e )
        {
            pom = pom.getAbsoluteFile();
        }

        mapBOMDependencyManagement( boms, session );
        final Set<File> result = modVersions( Collections.singletonList( pom ), pom.getParentFile(), session, true );
        if ( !result.isEmpty() )
        {
            final File out = result.iterator().next();

            LOGGER.info( "Modified POM versions.\n\n\tPOM: " + out + "\n\tBOMs:\t"
                            + StringUtils.join( boms.iterator(), "\n\t\t" ) + "\n\tPOM Backups: "
                            + session.getBackups() + "\n\n" );

            return out;
        }

        return null;
    }

    private Set<File> modVersions( final List<File> pomFiles, final File basedir, final VersionManagerSession session,
                                   final boolean preserveDirs )
    {
        final Set<File> result = new LinkedHashSet<File>();

        final DefaultProjectBuildingRequest req = new DefaultProjectBuildingRequest();
        req.setProcessPlugins( false );
        req.setRepositoryMerging( RepositoryMerging.POM_DOMINANT );
        req.setValidationLevel( ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_2_0 );

        try
        {
            req.setRepositorySession( serviceManager.createAetherRepositorySystemSession() );
        }
        catch ( final EMBEmbeddingException e )
        {
            session.setGlobalError( e );
            return result;
        }

        List<ProjectBuildingResult> projectResults;
        try
        {
            projectResults = projectBuilder.build( pomFiles, false, req );
        }
        catch ( final ProjectBuildingException e )
        {
            session.setGlobalError( e );
            return result;
        }

        final Map<String, String> depMap = session.getDependencyMap();
        for ( final ProjectBuildingResult projectResult : projectResults )
        {
            final List<ModelProblem> problems = projectResult.getProblems();
            if ( problems != null && !problems.isEmpty() )
            {
                for ( final ModelProblem problem : problems )
                {
                    final Exception cause = problem.getException();
                    session.getLog( projectResult.getPomFile() )
                           .add( "Problem interpolating model: %s\n%s %s @%s [%s:%s]\nReason: %s",
                                 problem.getModelId(), problem.getSeverity(), problem.getMessage(),
                                 problem.getSource(), problem.getLineNumber(), problem.getColumnNumber(),
                                 ( cause == null ? "??" : cause.getMessage() ) );
                }
                continue;
            }

            final MavenProject project = projectResult.getProject();
            final Model model = project.getOriginalModel();
            final File pom = projectResult.getPomFile();

            boolean changed = modifyCoord( model, depMap, pom, session );
            if ( model.getDependencies() != null )
            {
                for ( final Dependency dep : model.getDependencies() )
                {
                    final boolean modified = modifyDep( dep, depMap, pom, session, false );
                    changed = modified || changed;
                }
            }

            if ( model.getDependencyManagement() != null && model.getDependencyManagement().getDependencies() != null )
            {
                for ( final Dependency dep : model.getDependencyManagement().getDependencies() )
                {
                    final boolean modified = modifyDep( dep, depMap, pom, session, true );
                    changed = modified || changed;
                }
            }

            if ( changed )
            {
                final File out = writePom( model, pom, basedir, session, preserveDirs );
                if ( out != null )
                {
                    result.add( out );
                }
            }
        }

        return result;
    }

    private File writePom( final Model model, final File pom, final File basedir, final VersionManagerSession session,
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
                session.setError( pom, new VManException( "Failed to create backup subdirectory: %s", dir ) );
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
                session.setError( pom,
                                  new VManException( "Error making backup of POM: %s.\n\tTarget: %s\n\tReason: %s", e,
                                                     pom, backup, e.getMessage() ) );
                return null;
            }
        }

        String version = model.getVersion();
        if ( version == null && model.getParent() != null )
        {
            version = model.getParent().getVersion();
        }

        File outDir = pom.getParentFile();
        if ( !preserveDirs )
        {
            if ( outDir != null && !outDir.getName().equals( version ) )
            {
                try
                {
                    outDir = outDir.getCanonicalFile();
                }
                catch ( final IOException e )
                {
                    outDir = outDir.getAbsoluteFile();
                }

                final File parentDir = outDir.getParentFile();
                File newDir = null;

                if ( parentDir != null )
                {
                    newDir = new File( parentDir, version );
                }
                else
                {
                    newDir = new File( version );
                }

                outDir.renameTo( newDir );
                outDir = newDir;
            }
        }

        final File out = new File( outDir, model.getArtifactId() + "-" + version + ".pom" );
        final File oldPom = new File( outDir, pom.getName() );

        Writer writer = null;
        try
        {
            final SAXBuilder builder = new SAXBuilder();
            builder.setIgnoringBoundaryWhitespace( false );
            builder.setIgnoringElementContentWhitespace( false );

            final Document doc = builder.build( oldPom );

            String encoding = model.getModelEncoding();
            if ( encoding == null )
            {
                encoding = "UTF-8";
            }

            final Format format = Format.getRawFormat().setEncoding( encoding ).setTextMode( TextMode.PRESERVE );

            session.getLog( pom ).add( "Writing modified POM: %s", out );
            writer = WriterFactory.newWriter( out, encoding );

            new MavenJDOMWriter().write( model, doc, writer, format );

            if ( !out.equals( oldPom ) )
            {
                session.getLog( pom ).add( "Deleting original POM: %s", oldPom );
                oldPom.delete();
            }
        }
        catch ( final IOException e )
        {
            session.setError( pom,
                              new VManException( "Failed to write modified POM to: %s\n\tReason: %s", e, out,
                                                 e.getMessage() ) );
        }
        catch ( final JDOMException e )
        {
            session.setError( pom, new VManException( "Failed to read original POM for rewrite: %s\n\tReason: %s", e,
                                                      out, e.getMessage() ) );
        }
        finally
        {
            IOUtil.close( writer );
        }

        return out;
    }

    private boolean modifyDep( final Dependency dep, final Map<String, String> depMap, final File pom,
                               final VersionManagerSession session, final boolean isManaged )
    {
        boolean changed = false;

        final String key = dep.getManagementKey();
        String version = dep.getVersion();

        if ( version == null )
        {
            session.getLog( pom ).add( "NOT changing version for: %s%s. Version is inherited.", key,
                                       isManaged ? " [MANAGED]" : "" );
            return false;
        }

        version = depMap.get( key );
        if ( version != null )
        {
            if ( !version.equals( dep.getVersion() ) )
            {
                session.getLog( pom ).add( "Changing version for: %s%s.\n\tFrom: %s\n\tTo: %s.", key,
                                           isManaged ? " [MANAGED]" : "", dep.getVersion(), version );
                dep.setVersion( version );
                changed = true;
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

        return changed;
    }

    private boolean modifyCoord( final Model model, final Map<String, String> depMap, final File pom,
                                 final VersionManagerSession session )
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
            final String key = groupId + ":" + model.getArtifactId() + ":pom";

            String version = model.getVersion();
            version = depMap.get( key );

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
                    session.getLog( pom ).add( "POM version is already in line with BOM: %s", model.getVersion() );
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
            final String key = parent.getGroupId() + ":" + parent.getArtifactId() + ":pom";

            String version = parent.getVersion();
            version = depMap.get( key );
            if ( version == null )
            {
                session.setError( pom, new VManException( "INVALID POM: Missing parent version." ) );
                return false;
            }

            version = depMap.get( key );
            if ( version != null )
            {
                if ( !version.equals( model.getVersion() ) )
                {
                    session.getLog( pom ).add( "Changing POM parent (%s) version\n\tFrom: %s\n\tTo: %s", key,
                                               parent.getVersion(), version );
                    parent.setVersion( version );
                    changed = true;
                }
                else
                {
                    session.getLog( pom ).add( "POM parent (%s) version is correct: %s", key, parent.getVersion() );
                }
            }
            else
            {
                session.addMissingVersion( pom, key );
                session.getLog( pom ).add( "POM version is missing in BOM: %s", key );
            }
        }

        return changed;
    }

    private Map<String, String> mapBOMDependencyManagement( final List<File> boms, final VersionManagerSession session )
    {
        if ( !session.hasDependencyMap() )
        {
            final DefaultProjectBuildingRequest req = new DefaultProjectBuildingRequest();
            req.setProcessPlugins( false );
            req.setRepositoryMerging( RepositoryMerging.POM_DOMINANT );
            req.setValidationLevel( ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_2_0 );

            try
            {
                req.setRepositorySession( serviceManager.createAetherRepositorySystemSession() );
            }
            catch ( final EMBEmbeddingException e )
            {
                session.setGlobalError( e );
            }

            List<ProjectBuildingResult> projectResults;
            try
            {
                projectResults = projectBuilder.build( boms, false, req );

                for ( final ProjectBuildingResult projectResult : projectResults )
                {
                    final List<ModelProblem> problems = projectResult.getProblems();
                    if ( problems != null && !problems.isEmpty() )
                    {
                        for ( final ModelProblem problem : problems )
                        {
                            final Exception cause = problem.getException();
                            session.getLog( projectResult.getPomFile() )
                                   .add( "Problem interpolating model: %s\n%s %s @%s [%s:%s]\nReason: %s",
                                         problem.getModelId(), problem.getSeverity(), problem.getMessage(),
                                         problem.getSource(), problem.getLineNumber(), problem.getColumnNumber(),
                                         ( cause == null ? "??" : cause.getMessage() ) );
                        }

                        continue;
                    }

                    final File bom = projectResult.getPomFile();
                    final Model model = projectResult.getProject().getModel();

                    String groupId = model.getGroupId();
                    String version = model.getVersion();
                    final Parent parent = model.getParent();
                    if ( parent != null )
                    {
                        if ( groupId == null )
                        {
                            groupId = parent.getGroupId();
                        }

                        if ( version == null )
                        {
                            version = parent.getVersion();
                        }
                    }

                    session.startBomMap( bom, groupId + ":" + model.getArtifactId() + ":pom", version );

                    if ( model.getDependencyManagement() != null
                                    && model.getDependencyManagement().getDependencies() != null )
                    {
                        for ( final Dependency dep : model.getDependencyManagement().getDependencies() )
                        {
                            session.mapDependency( bom, dep );
                        }
                    }
                }
            }
            catch ( final ProjectBuildingException e )
            {
                session.setGlobalError( e );
            }
        }

        return session.getDependencyMap();
    }

    public String getId()
    {
        return "rh.vmod";
    }

    public String getName()
    {
        return "RedHat POM Version Modifier";
    }

}
