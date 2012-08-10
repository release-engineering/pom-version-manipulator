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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PropertyMappings
{

    private static final String EXPRESSION_PATTERN = "@([^@]+)@";

    private final Map<String, String> mappings = new HashMap<String, String>();

    private final Map<String, Boolean> literalFlags = new HashMap<String, Boolean>();

    private final VersionManagerSession session;

    public PropertyMappings( final Map<String, String> mappings, final VersionManagerSession session )
    {
        this.session = session;
        addMappings( mappings, session );
    }

    public PropertyMappings addBomPropertyMappings( final File bom, final Map<String, String> newMappings )
    {
        addMappings( newMappings, session );
        return this;
    }

    public String getMappingTarget( final String key )
    {
        return mappings.get( key );
    }

    public Boolean isLiteralMapping( final String key )
    {
        return literalFlags.get( key );
    }

    private void addMappings( final Map<String, String> newMappings, final VersionManagerSession session )
    {
        final Pattern pattern = Pattern.compile( EXPRESSION_PATTERN );

        for ( final Map.Entry<String, String> entry : newMappings.entrySet() )
        {
            String val = entry.getValue();
            boolean literal = true;
            final Matcher matcher = pattern.matcher( val );

            if ( matcher.matches() )
            {
                literal = false;
                val = matcher.group( 1 );
            }

            mappings.put( entry.getKey(), val );
            literalFlags.put( entry.getKey(), literal );
        }
    }

    public Set<String> getMappedKeys()
    {
        return mappings.keySet();
    }

}
