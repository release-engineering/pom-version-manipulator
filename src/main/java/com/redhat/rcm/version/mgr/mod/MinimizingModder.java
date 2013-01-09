/*
 *  Copyright (C) 2012 John Casey.
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

import java.util.Collections;

import org.apache.maven.model.Contributor;
import org.apache.maven.model.Developer;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectModder.class, hint = "minimize" )
public class MinimizingModder
    implements ProjectModder
{
    /**
     * {@inheritDoc}
     * @see com.redhat.rcm.version.mgr.mod.ProjectModder#inject(com.redhat.rcm.version.model.Project, com.redhat.rcm.version.mgr.session.VersionManagerSession)
     */
    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        boolean changed = false;
        final Model model = project.getModel();

        changed = ( new ReportingRemovalModder().inject( project, session ) );
        changed = ( new RepoRemovalModder().inject( project, session ) ) || changed;
        changed = ( new ExtensionsRemovalModder().inject( project, session ) ) || changed;

        if ( model.getDevelopers() != null )
        {
            model.setDevelopers( Collections.<Developer> emptyList() );
            changed = true;
        }

        if ( model.getContributors() != null )
        {
            model.setContributors( Collections.<Contributor> emptyList() );
            changed = true;
        }

        if ( model.getIssueManagement() != null )
        {
            model.setIssueManagement( null );
            changed = true;
        }

        if ( model.getCiManagement() != null )
        {
            model.setCiManagement( null );
            changed = true;
        }

        if ( model.getDistributionManagement() != null )
        {
            final DistributionManagement dm = model.getDistributionManagement();
            if ( dm.getRelocation() == null && dm.getStatus() == null )
            {
                model.setDistributionManagement( null );
                changed = true;
            }
            else
            {
                if ( dm.getDownloadUrl() != null )
                {
                    dm.setDownloadUrl( null );
                    changed = true;
                }

                if ( dm.getRepository() != null )
                {
                    dm.setRepository( null );
                    changed = true;
                }

                if ( dm.getSnapshotRepository() != null )
                {
                    dm.setSnapshotRepository( null );
                    changed = true;
                }

                if ( dm.getSite() != null )
                {
                    dm.setSite( null );
                    changed = true;
                }
            }
        }

        if ( model.getMailingLists() != null )
        {
            model.setMailingLists( Collections.<MailingList> emptyList() );
            changed = true;
        }

        if ( model.getOrganization() != null )
        {
            model.setOrganization( null );
            changed = true;
        }

        if ( model.getScm() != null )
        {
            model.setScm( null );
            changed = true;
        }

        if ( model.getUrl() != null )
        {
            model.setUrl( null );
            changed = true;
        }

        return changed;
    }

    @Override
    public String getDescription()
    {
        return "Remove all original site-production and infrastructural elements of the POM (mainly useful for re-release of a project by a third party).";
    }

}
