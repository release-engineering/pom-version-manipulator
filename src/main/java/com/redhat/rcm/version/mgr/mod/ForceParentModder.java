package com.redhat.rcm.version.mgr.mod;

import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectModder.class, hint = "force-parent-realignment" )
public class ForceParentModder
    implements ProjectModder
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Override
    public String getDescription()
    {
        return "Forcibly set the project's parent GAV using the declared toolchain GAV";
    }

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        boolean changed = false;

        if ( session.getToolchainKey() == null )
        {
            return changed;
        }

        final Model model = project.getModel();
        final FullProjectKey toolchainKey = session.getToolchainKey();

        Parent parent;

        if ( toolchainKey != null )
        {
            logger.info( "Injecting toolchain as parent for: " + project.getKey() );

            parent = new Parent();
            parent.setGroupId( toolchainKey.getGroupId() );
            parent.setArtifactId( toolchainKey.getArtifactId() );
            parent.setVersion( toolchainKey.getVersion() );

            model.setParent( parent );

            changed = true;
        }
        else
        {
            logger.info( "Toolchain not specified. Skipping toolchain-parent injection..." );
        }

        return changed;
    }

}
