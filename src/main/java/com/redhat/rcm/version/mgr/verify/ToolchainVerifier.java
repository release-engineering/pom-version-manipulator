package com.redhat.rcm.version.mgr.verify;

import java.util.Set;

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.util.CollectionToString;
import com.redhat.rcm.version.util.ObjectToString;

@Component( role = ProjectVerifier.class, hint = "toolchain-realignment" )
public class ToolchainVerifier
    implements ProjectVerifier
{

    @Override
    public void verify( final Project project, final VersionManagerSession session )
    {
        if ( session.getToolchainKey() == null )
        {
            // nothing to verify.
            return;
        }

        Set<VersionlessProjectKey> unmanaged = session.getUnmanagedPlugins( project.getPom() );
        if ( unmanaged != null && !unmanaged.isEmpty() )
        {
            session.addError( new VManException(
                                                 "The following plugins were NOT managed by the toolchain.\nProject: %s\nFile: %s\nPlugins:\n\n%s\n",
                                                 project.getKey(),
                                                 project.getPom(),
                                                 new CollectionToString<VersionlessProjectKey>(
                                                                                                unmanaged,
                                                                                                new ObjectToString<VersionlessProjectKey>() ) ) );
        }
    }

}
