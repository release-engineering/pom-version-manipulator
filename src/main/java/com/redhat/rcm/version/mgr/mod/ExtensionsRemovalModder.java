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
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectModder.class, hint = "extensions-removal" )
public class ExtensionsRemovalModder
    implements ProjectModder
{
    private static final Logger LOGGER = Logger.getLogger( ExtensionsRemovalModder.class );

    public String getDescription()
    {
        return "Remove <extensions/> elements from the POM.";
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

        if ( model.getBuild () != null && model.getBuild().getExtensions() != null &&
             !model.getBuild().getExtensions().isEmpty())
        {
            List<Extension> extensions = model.getBuild().getExtensions();
            Set<VersionlessProjectKey> whitelist = session.getExtensionsWhitelist();

            if (whitelist != null && ! whitelist.isEmpty())
            {
                Iterator<Extension> i = extensions.iterator();
                while (i.hasNext())
                {
                    Extension e = i.next();
                    VersionlessProjectKey key = new VersionlessProjectKey (e.getGroupId(), e.getArtifactId());

                    LOGGER.info( "ExtensionsRemoval - checking " + key +
                                 " against whitelist " + whitelist);

                    if ( ! whitelist.contains( key ) )
                    {
                        i.remove();
                        changed = true;
                    }
                    else
                    {
                        e.setVersion( session.replacePropertyVersion (project, e.getGroupId(), e.getArtifactId()));
                        changed = true;
                    }
                }
            }
            else
            {
                model.getBuild().setExtensions(Collections.<Extension>emptyList());
                changed = true;
            }
        }

        return changed;
    }

}
