package com.redhat.rcm.version.mgr.mod;

import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectModder.class, hint = "parent-realignment" )
public class ParentModder
    implements ProjectModder
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Override
    public String getDescription()
    {
        return "Set the project's parent GAV using the declared toolchain GAV";
    }

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        final Model model = project.getModel();
        final FullProjectKey toolchainKey = session.getToolchainKey();

        boolean changed = false;
        Parent parent = model.getParent();

        if ( toolchainKey != null )
        {
            if ( parent == null )
            {
                logger.info( "Injecting toolchain as parent for: " + project.getKey() );

                parent = new Parent();
                parent.setGroupId( toolchainKey.getGroupId() );
                parent.setArtifactId( toolchainKey.getArtifactId() );
                parent.setVersion( toolchainKey.getVersion() );

                model.setParent( parent );
                // session.addProject( project );

                changed = true;
            }
            else
            {
                final FullProjectKey fullParentKey = new FullProjectKey( parent );
                final FullProjectKey relocation = session.getRelocation( fullParentKey );
                if ( relocation != null )
                {
                    logger.info( "Relocating parent: " + parent + " to: " + relocation );

                    parent.setGroupId( relocation.getGroupId() );
                    parent.setArtifactId( relocation.getArtifactId() );
                    parent.setVersion( relocation.getVersion() );
                    changed = true;
                }

                final VersionlessProjectKey vtk = new VersionlessProjectKey( toolchainKey.getGroupId(), toolchainKey.getArtifactId() );

                final VersionlessProjectKey vpk = new VersionlessProjectKey( parent );

                if ( vtk.equals( vpk ) && !toolchainKey.equals( fullParentKey ) )
                {
                    parent.setVersion( toolchainKey.getVersion() );
                    changed = true;
                }
            }
        }
        else
        {
            logger.info( "Toolchain not specified. Skipping toolchain-parent injection..." );
        }

        return changed;
    }

}
