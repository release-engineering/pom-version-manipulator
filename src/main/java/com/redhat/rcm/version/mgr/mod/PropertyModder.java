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

import org.apache.log4j.Logger;
import org.apache.maven.model.Model;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.mgr.session.PropertyMappings;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

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

        final Properties currentModel = model.getProperties();
        final Set<String> commonKeys = currentModel.stringPropertyNames();

        final PropertyMappings propertyMappings = session.getPropertyMappings();
        commonKeys.retainAll( propertyMappings.getMappedKeys() );

        for ( final String key : commonKeys )
        {
            final String value = propertyMappings.getMappingTarget( key );
            final boolean isLiteral = propertyMappings.isLiteralMapping( key );

            if ( isLiteral )
            {
                LOGGER.info( "Replacing " + key + '/' + currentModel.get( key ) + " with LITERAL: '" + value + "'" );
                currentModel.put( key, value );
            }
            else
            {
                LOGGER.info( "Replacing " + key + '/' + currentModel.get( key ) + " with EXPRESSION: '${" + value
                    + "}'" );
                currentModel.put( key, "${" + value + "}" );
            }
            changed = true;
        }

        return changed;
    }

}
