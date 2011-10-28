package com.redhat.rcm.version.mgr.verify;

import org.apache.maven.mae.project.key.FullProjectKey;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectVerifier.class, hint = "version-suffix" )
public class VersionSuffixVerifier
    implements ProjectVerifier
{

    @Override
    public void verify( final Project project, final VersionManagerSession session )
    {
        if ( session.isMissingParent( project ) )
        {
            session.addError( new VManException(
                                                 "The project parent version was NOT specified in a BOM.\nParent: %s\nProject: %s\nFile: %s\n",
                                                 new FullProjectKey( project.getParent() ), project.getKey(),
                                                 project.getPom() ) );
        }
    }

}
