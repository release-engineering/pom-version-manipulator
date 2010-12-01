/*
 *  Copyright (C) 2010 John Casey.
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.redhat.rcm.version.mgr;

import org.apache.log4j.Logger;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.WriterFactory;
import org.commonjava.emb.EMBException;
import org.commonjava.emb.app.AbstractEMBApplication;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.report.Report;
import com.redhat.rcm.version.util.ModelProblemRenderer;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component( role = VersionManager.class )
public class VersionManager
    extends AbstractEMBApplication
{

    private static final Logger LOGGER = Logger.getLogger( VersionManager.class );

    @Requirement
    private ModelBuilder modelBuilder;

    @Requirement( role = Report.class )
    private Map<String, Report> reports;

    public VersionManager()
        throws EMBException
    {
        load();
    }

    public VersionManager( final ModelBuilder modelBuilder )
    {
        this.modelBuilder = modelBuilder;
    }

    public void generateReports( final File reportsDir, final VersionManagerSession sessionData )
    {
        if ( reports != null )
        {
            for ( final Map.Entry<String, Report> entry : reports.entrySet() )
            {
                try
                {
                    entry.getValue().generate( reportsDir, sessionData );
                }
                catch ( final VManException e )
                {
                    LOGGER.error( "Failed to generate report: " + entry.getKey(), e );
                }
            }
        }
    }

    public void modifyVersions( final File dir, final String pomNamePattern, final File bom,
                                final VersionManagerSession session )
    {
        final DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( dir );
        scanner.addDefaultExcludes();
        scanner.setIncludes( new String[] { pomNamePattern } );

        scanner.scan();

        mapBOMDependencyManagement( bom, session );

        final String[] includedSubpaths = scanner.getIncludedFiles();
        for ( final String subpath : includedSubpaths )
        {
            final File pom = new File( dir, subpath );
            modVersions( pom, dir, session, session.isPreserveDirs() );
        }
    }

    public void modifyVersions( final File pom, final File bom, final VersionManagerSession session )
    {
        mapBOMDependencyManagement( bom, session );
        modVersions( pom, pom.getParentFile(), session, true );
    }

    private void modVersions( final File pom, final File basedir, final VersionManagerSession session,
                              final boolean preserveDirs )
    {
        final Map<String, String> depMap = session.getDependencyMap();
        final ModelBuildingResult result = buildModel( pom, session );
        if ( depMap == null || result == null )
        {
            return;
        }

        final Model model = result.getRawModel();
        boolean changed = modifyCoord( model, depMap, pom, session );
        if ( model.getDependencies() != null )
        {
            for ( final Dependency dep : model.getDependencies() )
            {
                changed = changed || modifyDep( dep, depMap, pom, session, false );
            }
        }

        if ( model.getDependencyManagement() != null && model.getDependencyManagement().getDependencies() != null )
        {
            for ( final Dependency dep : model.getDependencyManagement().getDependencies() )
            {
                changed = changed || modifyDep( dep, depMap, pom, session, true );
            }
        }

        if ( changed )
        {
            writePom( model, pom, basedir, session );

            if ( !preserveDirs )
            {
                String version = model.getVersion();
                if ( version == null && model.getParent() != null )
                {
                    version = model.getParent().getVersion();
                }

                File dir = pom.getParentFile();
                if ( dir != null && !dir.getName().equals( version ) )
                {
                    try
                    {
                        dir = dir.getCanonicalFile();
                    }
                    catch ( final IOException e )
                    {
                        dir = dir.getAbsoluteFile();
                    }

                    final File parentDir = dir.getParentFile();
                    File newDir = null;

                    if ( parentDir != null )
                    {
                        newDir = new File( parentDir, version );
                    }
                    else
                    {
                        newDir = new File( version );
                    }

                    dir.renameTo( newDir );
                }
            }
        }
    }

    private void writePom( final Model model, final File pom, final File basedir, final VersionManagerSession session )
    {
        File out = pom;

        final File backupDir = session.getBackups();
        if ( backupDir != null )
        {
            String path = pom.getParent();
            path = path.substring( basedir.getPath().length() );

            final File dir = new File( backupDir, path );
            if ( !dir.mkdirs() )
            {
                session.setError( pom, new VManException( "Failed to create backup subdirectory: %s" ) );
                return;
            }

            out = new File( dir, pom.getName() );
            try
            {
                FileUtils.copyFile( pom, out );
            }
            catch ( final IOException e )
            {
                session.setError( pom, new VManException( "Error making backup of POM: %s.\nTarget: %s\nReason: %s", e,
                                                          pom, out, e.getMessage() ) );
                return;
            }
        }

        Writer writer = null;
        try
        {
            writer = WriterFactory.newXmlWriter( out );
            new MavenXpp3Writer().write( writer, model );
        }
        catch ( final IOException e )
        {
            session.setError( pom,
                              new VManException( "Failed to write modified POM to: %s\nReason: %s", e, out,
                                                 e.getMessage() ) );
        }
        finally
        {
            IOUtil.close( writer );
        }
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
                session.getLog( pom ).add( "Changing version for: %s%s.\nFrom: %s\nTo: %s.", key,
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
        if ( model.getGroupId() != null && model.getArtifactId() != null )
        {
            final String key = model.getGroupId() + ":" + model.getArtifactId() + ":pom";

            String version = model.getVersion();
            if ( version != null )
            {
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
                }
            }
            else
            {
                session.getLog( pom )
                       .add( "No POM version found. Any version change will have to happen in the parent reference" );
            }
        }

        final Parent parent = model.getParent();
        if ( parent != null )
        {
            final String key = parent.getGroupId() + ":" + parent.getArtifactId() + ":pom";

            String version = parent.getVersion();
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
                    session.getLog( pom ).add( "Changing POM parent (%s) version\nFrom: %s\nTo: %s", key,
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
            }
        }

        return changed;
    }

    private Map<String, String> mapBOMDependencyManagement( final File bom, final VersionManagerSession session )
    {
        Map<String, String> depMap = session.getDependencyMap();
        if ( depMap == null )
        {
            final ModelBuildingResult result = buildModel( bom, session );
            if ( result != null )
            {
                final Model model = result.getEffectiveModel();

                depMap = new HashMap<String, String>();

                if ( model.getDependencyManagement() != null
                                && model.getDependencyManagement().getDependencies() != null )
                {
                    for ( final Dependency dep : model.getDependencyManagement().getDependencies() )
                    {
                        depMap.put( dep.getGroupId() + ":" + dep.getArtifactId() + ":pom", dep.getVersion() );
                        depMap.put( dep.getManagementKey(), dep.getVersion() );
                    }
                }

                session.setDependencyMap( depMap );
            }
        }

        return depMap;
    }

    private ModelBuildingResult buildModel( final File pom, final VersionManagerSession session )
    {
        final DefaultModelBuildingRequest mbr = new DefaultModelBuildingRequest();
        mbr.setPomFile( pom );

        ModelBuildingResult result;
        try
        {
            result = modelBuilder.build( mbr );
        }
        catch ( final ModelBuildingException e )
        {
            session.setError( pom, e );
            result = null;
        }

        if ( result == null )
        {
            return null;
        }

        final List<ModelProblem> problems = result.getProblems();
        if ( problems != null && !problems.isEmpty() )
        {
            final ModelProblemRenderer renderer = new ModelProblemRenderer( problems, ModelProblem.Severity.ERROR );
            if ( renderer.containsProblemAboveThreshold() )
            {
                session.setError( pom, new VManException( "Encountered problems while reading POM:\n\n%s", renderer ) );
                return null;
            }
        }

        return result;
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
