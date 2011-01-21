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

package com.redhat.rcm.version.alias;

import static org.apache.commons.io.IOUtils.closeQuietly;

import org.apache.log4j.Logger;

import com.redhat.rcm.version.VManException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AliasMap
{

    private static final Logger LOGGER = Logger.getLogger( AliasMap.class );

    private final Map<String, String> aliases = new HashMap<String, String>();

    public AliasMap( final File aliasMapping )
        throws VManException
    {
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader( new FileReader( aliasMapping ) );

            String line = null;
            while ( ( line = reader.readLine() ) != null )
            {
                final String originalLine = line;

                int idx = line.indexOf( '#' );
                if ( idx > -1 )
                {
                    line = line.substring( 0, idx );
                }

                idx = line.indexOf( '=' );
                if ( idx > 0 )
                {
                    final String key = line.substring( 0, idx ).trim();
                    final String val = line.substring( idx + 1 ).trim();
                    aliases.put( key, val );
                }
                else
                {
                    LOGGER.warn( "Invalid alias mapping: '" + originalLine + "'." );
                }
            }
        }
        catch ( final IOException e )
        {
            throw new VManException( "Failed to read alias-mapping file: %s\nReason: %s", e, aliasMapping,
                                     e.getMessage() );
        }
        finally
        {
            closeQuietly( reader );
        }
    }

    public String getAlias( final String groupId )
    {
        final String result = aliases.get( groupId );
        if ( result == null )
        {
            return groupId;
        }

        return result;
    }

}
