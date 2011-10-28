package com.redhat.rcm.version.mgr.verify;

import java.util.Set;

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.util.CollectionToString;
import com.redhat.rcm.version.util.ObjectToString;

@Component( role = ProjectVerifier.class, hint = "BOM-realignment" )
public class BomVerifier
    implements ProjectVerifier
{

    @Override
    public void verify( final Project project, final VersionManagerSession session )
    {
        Set<VersionlessProjectKey> missing = session.getMissingVersions( project.getKey() );
        if ( missing != null && !missing.isEmpty() )
        {
            session.addError( new VManException(
                                                 "The following dependencies were NOT found in a BOM.\nProject: %s\nFile: %s\nDependencies:\n\n%s\n",
                                                 project.getKey(),
                                                 project.getPom(),
                                                 new CollectionToString<VersionlessProjectKey>(
                                                                                                missing,
                                                                                                new ObjectToString<VersionlessProjectKey>() ) ) );
        }
    }

}
