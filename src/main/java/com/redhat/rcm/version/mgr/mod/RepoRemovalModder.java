/*
 *  Copyright (C) 2011 John Casey.
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.redhat.rcm.version.mgr.mod;

import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

import java.util.ArrayList;

@Component( role = ProjectModder.class, hint = "repo-removal" )
public class RepoRemovalModder
    implements ProjectModder
{

    public String getDescription()
    {
        return "Remove <repositories/> and <pluginRepostories/> elements from the POM (this is a Maven best practice).";
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.redhat.rcm.version.mgr.mod.ProjectModder#inject(com.redhat.rcm.version.model.Project,
     *      com.redhat.rcm.version.mgr.session.VersionManagerSession)
     */
    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        final Model model = project.getModel();

        boolean changed = false;
        if ( model.getRepositories() != null && !model.getRepositories().isEmpty() )
        {
            model.setRepositories( new ArrayList<Repository>() );
            changed = true;
        }

        if ( model.getPluginRepositories() != null && !model.getPluginRepositories().isEmpty() )
        {
            model.setPluginRepositories( new ArrayList<Repository>() );
            changed = true;
        }

        return changed;
    }

}
