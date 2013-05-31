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

package com.redhat.rcm.version.mgr.verify;

import java.util.HashSet;
import java.util.LinkedHashSet;
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

        final LinkedHashSet<Project> currentProjects = session.getCurrentProjects();
        final Set<VersionlessProjectKey> currentKeys = new HashSet<VersionlessProjectKey>();

        for ( final Project p : currentProjects )
        {
            currentKeys.add( p.getVersionlessKey() );
        }

        final Set<VersionlessProjectKey> unmanaged = session.getUnmanagedPlugins( project.getPom() );
        if ( unmanaged != null && !unmanaged.isEmpty() )
        {
            int count = 0;
            for ( final VersionlessProjectKey up : unmanaged )
            {
                if ( !currentKeys.contains( up ) )
                {
                    count++;
                }
            }

            if ( count > 0 )
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

}
