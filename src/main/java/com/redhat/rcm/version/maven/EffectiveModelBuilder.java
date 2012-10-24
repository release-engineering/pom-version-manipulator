package com.redhat.rcm.version.maven;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = EffectiveModelBuilder.class )
public class EffectiveModelBuilder
{

    @Requirement
    private ModelBuilder modelBuilder;

    public Model getEffectiveModel( final Project project, final VersionManagerSession session )
        throws VManException
    {
        Model effModel = project.getEffectiveModel();
        if ( effModel == null )
        {
            final DefaultModelBuildingRequest mbr = new DefaultModelBuildingRequest();
            mbr.setSystemProperties( System.getProperties() );
            mbr.setPomFile( project.getPom() );
            mbr.setValidationLevel( ModelBuildingRequest.VALIDATION_LEVEL_STRICT );
            mbr.setProcessPlugins( false );

            try
            {
                final ModelBuildingResult result = modelBuilder.build( mbr );
                effModel = result.getEffectiveModel();
                project.setEffectiveModel( effModel );
            }
            catch ( final ModelBuildingException e )
            {
                throw new VManException( "Failed to build effective model for: %s. Reason: %s", e, project.getKey(),
                                         e.getMessage() );
            }
        }

        return effModel;
    }

}
