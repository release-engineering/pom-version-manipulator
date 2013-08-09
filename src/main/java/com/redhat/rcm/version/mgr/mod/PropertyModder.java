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

import java.util.Properties;
import java.util.Set;

import org.apache.maven.model.Model;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.rcm.version.mgr.session.PropertyMappings;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectModder.class, hint = "property" )
public class PropertyModder
    implements ProjectModder
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Override
    public String getDescription()
    {
        return "Change property mappings to use those declared in the supplied BOM file(s).";
    }

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        final Model model = project.getModel();
        boolean changed = false;

        final Properties currentModel = model.getProperties();
        final Set<String> commonKeys = currentModel.stringPropertyNames();

        final PropertyMappings propertyMappings = session.getPropertyMappings();
        commonKeys.retainAll( propertyMappings.getKeys() );

        for ( final String key : commonKeys )
        {
            final String value = propertyMappings.getMappedValue( key, session );

            if ( value != null )
            {
                logger.info( "Replacing " + key + '/' + currentModel.get( key ) + " with: '" + value + "'" );
                currentModel.put( key, value );
                changed = true;
            }
        }

        return changed;
    }

}
