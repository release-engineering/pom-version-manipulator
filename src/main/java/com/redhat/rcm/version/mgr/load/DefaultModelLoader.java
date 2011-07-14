/*
 *  Copyright (C) 2011 John Casey.
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

package com.redhat.rcm.version.mgr.load;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.VersionManagerSession;
import com.redhat.rcm.version.mgr.model.Project;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

@Component( role=ModelLoader.class )
public class DefaultModelLoader
    implements ModelLoader
{
    
    @Requirement
    private ModelBuilder modelBuilder;

    @Override
    public List<Project> buildModels( VersionManagerSession session, File... poms )
        throws VManException
    {
        List<Project> projects = new ArrayList<Project>();
        
        DefaultModelBuildingRequest template =
            new DefaultModelBuildingRequest().setValidationLevel( ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL );
        
        LinkedList<File> allPoms = new LinkedList<File>( Arrays.asList( poms ) );
        while( !allPoms.isEmpty() )
        {
            File pom = allPoms.removeFirst();
            
            Model model;
            
            DefaultModelBuildingRequest request = new DefaultModelBuildingRequest( template );
            try
            {
                ModelBuildingResult result = modelBuilder.build( request );
                model = result.getRawModel();
            }
            catch ( ModelBuildingException e )
            {
                throw new VManException( "Cannot build model from POM: %s. Reason: %s", e, pom, e.getMessage() );
            }
            
            projects.add( new Project( pom, model ) );
            if ( session.isProjectBuildRecursive() && model.getModules() != null && !model.getModules().isEmpty() )
            {
                File dir = pom.getParentFile();
                if ( dir == null )
                {
                    dir = new File( System.getProperty( "user.dir" ) ).getAbsoluteFile();
                }
                
                for ( String module : model.getModules() )
                {
                    File modPom = new File( dir, module );
                    if ( modPom.isDirectory() )
                    {
                        modPom = new File( modPom, "pom.xml" );
                    }
                    
                    if ( modPom.exists() )
                    {
                        allPoms.add( modPom );
                    }
                }
            }
        }
        
        return projects;
    }

}
