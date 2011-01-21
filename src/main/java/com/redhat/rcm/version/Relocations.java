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

package com.redhat.rcm.version;

import org.apache.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class Relocations
{

    private static final Logger LOGGER = Logger.getLogger( Relocations.class );

    private final Map<Coord, Coord> relocations = new HashMap<Coord, Coord>();

    private final Map<File, Map<Coord, Coord>> byFile = new LinkedHashMap<File, Map<Coord, Coord>>();

    private Coord toCoord( final String src )
        throws VManException
    {
        final String[] parts = src.split( ":" );
        if ( parts.length != 2 )
        {
            throw new VManException( "Invalid coordinate: '" + src + "'." );
        }

        parts[0] = parts[0].trim();
        parts[1] = parts[1].trim();
        if ( parts[0].length() < 1 || parts[1].length() < 1 )
        {
            throw new VManException( "Invalid coordinate: '" + src + "'." );
        }

        return new Coord( parts[0], parts[1] );
    }

    public Coord getRelocation( final String groupId, final String artifactId )
    {
        return getRelocation( new Coord( groupId, artifactId ) );
    }

    public Coord getRelocation( final Coord key )
    {
        return relocations.get( key );
    }

    public Relocations addBomRelocations( final File bom, final String relocationsStr )
        throws VManException
    {
        final String[] lines = relocationsStr.split( "[\\s,]+" );
        if ( lines != null && lines.length > 0 )
        {
            LOGGER.info( bom + ": Found " + lines.length + " relocations..." );
            final Map<Coord, Coord> relocations = new LinkedHashMap<Coord, Coord>();
            for ( String line : lines )
            {
                LOGGER.info( "processing: '" + line + "'" );
                int idx = line.indexOf( '#' );
                if ( idx > -1 )
                {
                    line = line.substring( 0, idx );
                }

                idx = line.indexOf( '=' );
                if ( idx > 0 )
                {
                    final Coord key = toCoord( line.substring( 0, idx ).trim() );
                    final Coord val = toCoord( line.substring( idx + 1 ).trim() );

                    LOGGER.info( "Adding relocation from: " + key + " to: " + val + " in BOM: " + bom );
                    relocations.put( key, val );
                }
            }

            this.relocations.putAll( relocations );
            byFile.put( bom, relocations );
        }
        else
        {
            LOGGER.info( bom + ": No relocations found" );
        }

        return this;
    }

    public Map<File, Map<Coord, Coord>> getRelocationsByFile()
    {
        return byFile;
    }

}
