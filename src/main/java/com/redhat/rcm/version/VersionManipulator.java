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

package com.redhat.rcm.version;

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

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component( role = VersionManipulator.class )
public class VersionManipulator
    extends AbstractEMBApplication
{

    @Requirement
    private ModelBuilder modelBuilder;

    public VersionManipulator()
        throws EMBException
    {
        load();
    }

    public void modifyVersions( final File dir, final String pomNamePattern, final File bom,
                                final ManipulationSession session )
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
            modVersions( pom, dir, session );
        }
    }

    public void modifyVersions( final File pom, final File bom, final ManipulationSession session )
    {
        mapBOMDependencyManagement( bom, session );
        modVersions( pom, pom.getParentFile(), session );
    }

    private void modVersions( final File pom, final File basedir, final ManipulationSession session )
    {
        final Map<String, String> depMap = session.getDependencyMap();
        final ModelBuildingResult result = buildModel( pom, session );
        if ( depMap == null || result == null )
        {
            return;
        }

        final Model model = result.getRawModel();
        modifyCoord( model, depMap, pom, session );

        if ( model.getDependencies() != null )
        {
            for ( final Dependency dep : model.getDependencies() )
            {
                modifyDep( dep, depMap, pom, session, false );
            }
        }

        if ( model.getDependencyManagement() != null && model.getDependencyManagement().getDependencies() != null )
        {
            for ( final Dependency dep : model.getDependencyManagement().getDependencies() )
            {
                modifyDep( dep, depMap, pom, session, true );
            }
        }

        writePom( model, pom, basedir, session );
    }

    private void writePom( final Model model, final File pom, final File basedir, final ManipulationSession session )
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
                session.addError( pom, new VersionManipulationException( "Failed to create backup subdirectory: %s" ) );
                return;
            }

            out = new File( dir, pom.getName() );
            try
            {
                FileUtils.copyFile( pom, out );
            }
            catch ( final IOException e )
            {
                session.addError( pom,
                                  new VersionManipulationException(
                                                                    "Error making backup of POM: %s.\nTarget: %s\nReason: %s",
                                                                    e, pom, out, e.getMessage() ) );
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
            session.addError( pom, new VersionManipulationException( "Failed to write modified POM to: %s\nReason: %s",
                                                                     e, out, e.getMessage() ) );
        }
        finally
        {
            IOUtil.close( writer );
        }
    }

    private void modifyDep( final Dependency dep, final Map<String, String> depMap, final File pom,
                            final ManipulationSession session, final boolean isManaged )
    {
        String version = dep.getVersion();
        if ( version == null )
        {
            return;
        }

        final String key = dep.getManagementKey();

        version = depMap.get( key );
        if ( version != null )
        {
            dep.setVersion( version );
        }
        else
        {
            session.addMissingVersion( pom, key );
        }
    }

    private void modifyCoord( final Model model, final Map<String, String> depMap, final File pom,
                              final ManipulationSession session )
    {
        final Parent parent = model.getParent();
        String groupId = model.getGroupId();
        String artifactId = model.getArtifactId();

        if ( parent != null )
        {
            if ( groupId == null )
            {
                groupId = parent.getGroupId();
            }

            if ( artifactId == null )
            {
                artifactId = parent.getArtifactId();
            }
        }

        if ( groupId == null || artifactId == null )
        {
            session.addError( pom, new VersionManipulationException( "INVALID POM: Missing groupId or artifactId." ) );
            return;
        }

        final String key = groupId + ":" + artifactId + ":pom";

        String version = model.getVersion();
        if ( version != null )
        {
            version = depMap.get( key );
            if ( version != null )
            {
                model.setVersion( version );
            }
            else
            {
                session.addMissingVersion( pom, key );
            }
        }
        else
        {
            version = parent.getVersion();
            if ( version == null )
            {
                session.addError( pom,
                                  new VersionManipulationException( "INVALID POM: Missing version / parent version." ) );
                return;
            }

            version = depMap.get( key );
            if ( version != null )
            {
                parent.setVersion( version );
            }
            else
            {
                session.addMissingVersion( pom, key );
            }
        }
    }

    private Map<String, String> mapBOMDependencyManagement( final File bom, final ManipulationSession session )
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

    private ModelBuildingResult buildModel( final File pom, final ManipulationSession session )
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
            session.addError( pom, e );
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
                session.addError( pom,
                                  new VersionManipulationException( "Encountered problems while reading POM:\n\n%s",
                                                                    renderer ) );
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
