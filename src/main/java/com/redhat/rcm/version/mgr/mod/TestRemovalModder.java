/*
 * Copyright (c) 2011 Red Hat, Inc.
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

import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectModder.class, hint = "testremoval" )
public class TestRemovalModder
    implements ProjectModder
{
    private static final Logger LOGGER = Logger.getLogger( TestRemovalModder.class );

    @Override
    public String getDescription()
    {
        return "Forcibly remove test scoped dependencies from the pom.";
    }

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        final Model model = project.getModel();
        boolean changed = false;
        final Set<VersionlessProjectKey> removedTests = session.getRemovedTests();
        final VersionlessProjectKey projectkey = new VersionlessProjectKey 
                        (project.getGroupId(), project.getArtifactId());

        if (removedTests.contains(projectkey))
        {
            if ( model.getDependencies() != null )
            {
                for ( final Iterator<Dependency> it = model.getDependencies()
                                .iterator(); it.hasNext(); )
                {
                    final Dependency dep = it.next();
                    if (dep.getScope() != null && dep.getScope().equals( "test" ))
                    {
                        LOGGER.info( "Removing scoped test dependency " + dep.toString() + " for '" + project.getKey() + "'..." );
                        it.remove();
                    }
                }
            }
            Properties props = model.getProperties();
            
            LOGGER.info( "Injecting new maven.skip.test property..." );
            if (props == null)
            {
                props = new Properties();
            }
            props.put("maven.skip.test", "true");
            model.setProperties( props );
            
            changed = true;
        }
        return changed;
    }
}
