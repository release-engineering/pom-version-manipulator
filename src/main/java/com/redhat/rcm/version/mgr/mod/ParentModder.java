/*
 * Copyright (c) 2014 Red Hat, Inc.
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

import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectModder.class, hint = "parent-injection" )
public class ParentModder
    implements ProjectModder
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Override
    public String getDescription()
    {
        return "Forcibly change POM to inject the supplied parent (if none exists).";
    }

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        boolean changed = false;

        if ( session.getToolchainKey() == null )
        {
            return changed;
        }

        changed = attemptToolchainParentInjection( project, session ) || changed;

        return changed;
    }

    private boolean attemptToolchainParentInjection( final Project project, final VersionManagerSession session )
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
