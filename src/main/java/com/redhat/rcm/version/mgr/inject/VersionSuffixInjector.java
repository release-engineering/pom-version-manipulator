package com.redhat.rcm.version.mgr.inject;

import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.mgr.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectInjector.class, hint = "version-suffix" )
public class VersionSuffixInjector
    implements ProjectInjector
{

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        boolean changed = false;
        if ( session.getVersionSuffix() != null )
        {
            String suffix = session.getVersionSuffix();
            Model model = project.getModel();
            Parent parent = project.getParent();

            if ( model.getVersion() != null && !model.getVersion().endsWith( suffix ) )
            {
                model.setVersion( model.getVersion() + suffix );
                changed = true;
            }

            if ( parent != null )
            {
                ProjectKey tk = session.getToolchainKey();
                VersionlessProjectKey vpk = new VersionlessProjectKey( parent );
                String version = session.getArtifactVersion( vpk );

                if ( tk == null || new VersionlessProjectKey( tk ).equals( vpk ) )
                {
                    // NOP.
                }
                else if ( version == null )
                {
                    session.addMissingParent( project );
                }
                else if ( !parent.getVersion().equals( version ) )
                {
                    model.getParent().setVersion( version );
                    changed = true;
                }
            }
        }

        return changed;
    }

}
