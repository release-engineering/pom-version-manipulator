/*
 * Copyright (c) 2010 Red Hat, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see 
 * <http://www.gnu.org/licenses>.
 */

package com.redhat.rcm.version.mgr.mod;

import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectModder.class, hint = "version-suffix" )
public class VersionSuffixModder
    implements ProjectModder
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
                else if ( session.inCurrentSession( parent ) )
                {
                    if ( !parent.getVersion().endsWith( suffix ) )
                    {
                        parent.setVersion( parent.getVersion() + suffix );
                        changed = true;
                    }
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
