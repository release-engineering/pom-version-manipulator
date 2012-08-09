/*
 * Copyright (c) 2012 Red Hat, Inc.
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

import static com.redhat.rcm.version.mgr.mod.Interpolations.interpolate;

import org.apache.log4j.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.model.ReadOnlyDependency;

import java.io.File;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

@Component( role = ProjectModder.class, hint = "property" )
public class PropertyModder
    implements ProjectModder
{
    private static final Logger LOGGER = Logger.getLogger( BomModder.class );

    public String getDescription()
    {
        return "Change property mappings to use those declared in the supplied BOM file(s).";
    }

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        final Model model = project.getModel();
        boolean changed = false;

        Properties currentModel = model.getProperties();
        Set<String> commonKeys = currentModel.stringPropertyNames();
        commonKeys.retainAll(session.getPropertyMapping().keySet());

        for (String key : commonKeys)
        {
            LOGGER.info ("Replacing " + key + '/' +
                         currentModel.get(key) + " with " +  session.getPropertyMapping().get(key).getKey() + '/' +
                         session.getPropertyMapping().get(key).getValue());

            // In case the property is used elsewhere as a value just update it to the new value.
            currentModel.put (key, session.getPropertyMapping().get(key).getValue());

            changed = true;
        }

        return changed;
    }

}
