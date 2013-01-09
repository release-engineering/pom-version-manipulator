package com.redhat.rcm.version.maven;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.aether.impl.ArtifactResolver;
import org.sonatype.aether.impl.RemoteRepositoryManager;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = EffectiveModelBuilder.class )
public class EffectiveModelBuilder
{

    @Requirement
    private ModelBuilder modelBuilder;

    @Requirement
    private ArtifactResolver artifactResolver;

    @Requirement
    private RemoteRepositoryManager remoteRepositoryManager;

    public void loadEffectiveModel( final Project project, final VersionManagerSession session )
        throws VManException
    {
        Model effModel = project.getEffectiveModel();
        if ( effModel == null )
        {
            final DefaultModelBuildingRequest mbr = new DefaultModelBuildingRequest();
            mbr.setSystemProperties( System.getProperties() );
            mbr.setModelSource( new ProjectModelSource( project ) );
            mbr.setValidationLevel( ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL );
            mbr.setProcessPlugins( false );

            final VManModelResolver resolver =
                new VManModelResolver( session, project, artifactResolver, remoteRepositoryManager );

            mbr.setModelResolver( resolver );

            try
            {
                final ModelBuildingResult result = modelBuilder.build( mbr );
                effModel = result.getEffectiveModel();
                project.setEffectiveModel( effModel );
            }
            catch ( final ModelBuildingException e )
            {
                throw new VManException( "Failed to build effective model for: %s (POM: %s). Reason: %s", e,
                                         project.getKey(), project.getPom(), e.getMessage() );
            }
        }
    }

}
