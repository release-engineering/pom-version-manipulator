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
import org.apache.maven.mae.boot.embed.MAEEmbeddingException;
import org.apache.maven.mae.boot.services.MAEServiceManager;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
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
import org.commonjava.emb.app.AbstractMAEApplication;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.Format.TextMode;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.model.FullProjectKey;
import com.redhat.rcm.version.model.ProjectKey;
import com.redhat.rcm.version.model.VersionlessProjectKey;
import com.redhat.rcm.version.report.Report;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private ProjectBuilder projectBuilder;

    @Requirement
    private MAEServiceManager serviceManager;

    @Requirement( role = Report.class )
    private Map<String, Report> reports;

    private static Object lock = new Object();

    private static VersionManager instance;

    private VersionManager()
    {
    }

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

    public Set<File> modifyVersions( final File dir, final String pomNamePattern, final List<File> boms,
                                     final VersionManagerSession session )
    {
        final DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( dir );
        scanner.addDefaultExcludes();
        scanner.setIncludes( new String[] { pomNamePattern } );

        scanner.scan();

        loadBOMs( boms, session );

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

        final Set<File> outFiles = modVersions( pomFiles, dir, session, session.isPreserveFiles() );

        LOGGER.info( "Modified " + outFiles.size() + " POM versions in directory.\n\n\tDirectory: " + dir
                        + "\n\tBOMs:\t" + StringUtils.join( boms.iterator(), "\n\t\t" ) + "\n\tPOM Backups: "
                        + session.getBackups() + "\n\n" );

        return outFiles;
    }

    public Set<File> modifyVersions( File pom, final List<File> boms, final VersionManagerSession session )
    {
        try
        {
            pom = pom.getCanonicalFile();
        }
        catch ( final IOException e )
        {
            pom = pom.getAbsoluteFile();
        }

        loadBOMs( boms, session );
        final Set<File> result = modVersions( Collections.singletonList( pom ), pom.getParentFile(), session, true );
        if ( !result.isEmpty() )
        {
            final File out = result.iterator().next();

            LOGGER.info( "Modified POM versions.\n\n\tTop POM: " + out + "\n\tBOMs:\t"
                            + StringUtils.join( boms.iterator(), "\n\t\t" ) + "\n\tPOM Backups: "
                            + session.getBackups() + "\n\n" );

            return result;
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
        catch ( final MAEEmbeddingException e )
        {
            session.addGlobalError( e );
            return result;
        }

        List<ProjectBuildingResult> projectResults;
        try
        {
            projectResults = projectBuilder.build( pomFiles, false, req );
        }
        catch ( final ProjectBuildingException e )
        {
            session.addGlobalError( e );
            return result;
        }

        final Map<FullProjectKey, MavenProject> projects = new LinkedHashMap<FullProjectKey, MavenProject>();
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
            projects.put( new FullProjectKey( project ), project );
        }

        for ( final MavenProject project : projects.values() )
        {
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
                introduceBoms( model, projects, pom, session );
            }

            if ( model.getDependencies() != null )
            {
                for ( final Dependency dep : model.getDependencies() )
                {
                    final boolean modified = modifyDep( dep, pom, session, false );
                    changed = modified || changed;
                }
            }

            if ( model.getDependencyManagement() != null && model.getDependencyManagement().getDependencies() != null )
            {
                for ( final Dependency dep : model.getDependencyManagement().getDependencies() )
                {
                    final boolean modified = modifyDep( dep, pom, session, true );
                    changed = modified || changed;
                }
            }

            if ( changed )
            {
                final File out = writePom( model, originalCoord, originalVersion, pom, basedir, session, preserveDirs );
                if ( out != null )
                {
                    result.add( out );
                }
            }
        }

        return result;
    }

    private void introduceBoms( final Model model, final Map<FullProjectKey, MavenProject> projects, final File pom,
                                final VersionManagerSession session )
    {
        // TODO: If the parent exists in the project-set, ignore the current project (modify the parent instead...)
        final Parent parent = model.getParent();
        if ( parent != null )
        {
            final FullProjectKey key = new FullProjectKey( parent );
            if ( projects.containsKey( key ) )
            {
                return;
            }
        }

        // TODO: check for all BOMs in use in this session...where missing, introduce them at the top of the multimodule
        // hierarchy.
        final Set<FullProjectKey> boms = new LinkedHashSet<FullProjectKey>( session.getBomCoords() );
        DependencyManagement dm = model.getDependencyManagement();
        if ( dm != null )
        {
            final List<Dependency> deps = dm.getDependencies();
            if ( deps != null )
            {
                for ( final Dependency dep : deps )
                {
                    if ( dep.getVersion() != null && Artifact.SCOPE_IMPORT.equals( dep.getScope() )
                                    && "pom".equals( dep.getType() ) )
                    {
                        final FullProjectKey k = new FullProjectKey( dep );
                        boms.remove( k );
                    }
                }
            }
            else
            {
                dm = new DependencyManagement();
            }

            for ( final FullProjectKey bk : boms )
            {
                dm.addDependency( bk.getBomDependency() );
            }
        }
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
                                  new VManException( "Error making backup of POM: %s.\n\tTarget: %s\n\tReason: %s", e,
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
                              new VManException( "Failed to write modified POM to: %s\n\tReason: %s", e, out,
                                                 e.getMessage() ) );
        }
        catch ( final JDOMException e )
        {
            session.addError( pom, new VManException( "Failed to read original POM for rewrite: %s\n\tReason: %s", e,
                                                      out, e.getMessage() ) );
        }
        finally
        {
            IOUtil.close( writer );
        }

        return out;
    }

    private boolean modifyDep( final Dependency dep, final File pom, final VersionManagerSession session,
                               final boolean isManaged )
    {
        boolean changed = false;

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
            session.getLog( pom ).add( "NOT changing version for: %s%s. Version is inherited.", key,
                                       isManaged ? " [MANAGED]" : "" );
            return false;
        }

        version = session.getArtifactVersion( key );
        if ( version != null )
        {
            if ( !version.equals( dep.getVersion() ) )
            {
                session.getLog( pom ).add( "Changing version for: %s%s.\n\tFrom: %s\n\tTo: %s.", key,
                                           isManaged ? " [MANAGED]" : "", dep.getVersion(), version );

                if ( session.isNormalizeBomUsage() )
                {
                    // wipe this out, and use the one in the BOM implicitly...DRY-style.
                    dep.setVersion( null );
                }
                else
                {
                    dep.setVersion( version );
                }
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
        }

        return changed;
    }

    private void loadBOMs( final List<File> boms, final VersionManagerSession session )
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
            catch ( final MAEEmbeddingException e )
            {
                session.addGlobalError( e );
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
                    final MavenProject project = projectResult.getProject();
                    LOGGER.info( "Adding BOM to session: " + bom + "; " + project );
                    session.addBOM( bom, project );
                }
            }
            catch ( final ProjectBuildingException e )
            {
                session.addGlobalError( e );
            }
        }
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
