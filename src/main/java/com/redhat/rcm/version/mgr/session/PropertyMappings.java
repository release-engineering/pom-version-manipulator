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

package com.redhat.rcm.version.mgr.session;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.MapBasedValueSource;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.rcm.version.VManException;

public class PropertyMappings
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private static final String EXPRESSION_PATTERN = "@([^@]+)@";

    private final Map<String, String> mappings = new HashMap<String, String>();

    private final Map<String, String> expressions = new HashMap<String, String>();

    public PropertyMappings( final Map<String, String> newMappings )
    {
        addMappings( null, newMappings );
    }

    PropertyMappings addBomPropertyMappings( final File bom, final Properties properties )
    {
        return addBomPropertyMappings( bom, properties, mappings );
    }
    
    PropertyMappings addBomPropertyMappings( final File bom, final Properties properties,
                                             final Map<String, String> newMappings )
    {
        addMappings( properties, newMappings );

        final Map<String, String> fromProps = new HashMap<String, String>();
        for ( final String key : properties.stringPropertyNames() )
        {
            fromProps.put( key, properties.getProperty( key ) );
        }

        // Add the BOM's own properties into the mappings...
        addMappings( properties, fromProps );

        return this;
    }

    public String getMappedValue( final String key, final VersionManagerSession session )
    {
        final String raw = mappings.get( key );

        if ( raw == null )
        {
            return null;
        }

        final StringSearchInterpolator interpolator = new StringSearchInterpolator( "@", "@" );
        interpolator.addValueSource( new MapBasedValueSource( mappings ) );
        try
        {
            return interpolator.interpolate( raw );
        }
        catch ( final InterpolationException e )
        {
            logger.error( "Invalid expression: '%s'. Reason: %s", e, raw, e.getMessage() );
            session.addError( new VManException( "Invalid expression: '%s'. Reason: %s", e, raw, e.getMessage() ) );
        }

        return null;
    }

    private void addMappings( final Properties properties, final Map<String, String> newMappings )
    {
        final Pattern pattern = Pattern.compile( EXPRESSION_PATTERN );

        if ( newMappings != null )
        {
            for ( final Map.Entry<String, String> entry : newMappings.entrySet() )
            {
                final String val = entry.getValue();
                final Matcher matcher = pattern.matcher( val );

                if ( matcher.matches() )
                {
                    final String k = matcher.group( 1 );
                    if ( ( !mappings.containsKey( k ) && !newMappings.containsKey( k ) ) ||
                    // Its also an expression if the property exists in the global properties map.
                        ( properties != null && properties.containsKey( k ) ) )
                    {
                        expressions.put( entry.getKey(), matcher.group( 1 ) );
                    }
                    else
                    {
                        mappings.put( entry.getKey(), val );
                    }
                }
                else
                {
                    mappings.put( entry.getKey(), val );
                }
            }
        }
    }

    public Set<String> getKeys()
    {
        return mappings.keySet();
    }

    /*
     * This method should take a properties from a BOM and look through that to update the mappings value with the real
     * value.
     */
    void updateProjectMap( final Properties properties )
    {
        final Set<Map.Entry<String, String>> contents = expressions.entrySet();
        for ( final Iterator<Map.Entry<String, String>> i = contents.iterator(); i.hasNext(); )
        {
            final Map.Entry<String, String> v = i.next();
            final String value = properties.getProperty( v.getValue() );

            if ( value == null )
            {
                continue;
            }

            mappings.put( v.getKey(), value );

            logger.info( "Replacing " + v.getKey() + " with value from " + v.getValue() + '('
                + mappings.get( v.getKey() ) + ')' );

            i.remove();
        }
    }
}
