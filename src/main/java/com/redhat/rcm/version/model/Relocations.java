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

package com.redhat.rcm.version.model;

import org.apache.log4j.Logger;

import com.redhat.rcm.version.VManException;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class Relocations
{

    private static final Logger LOGGER = Logger.getLogger( Relocations.class );

    private final Map<VersionlessProjectKey, VersionlessProjectKey> relocations = new HashMap<VersionlessProjectKey, VersionlessProjectKey>();

    private final Map<File, Map<VersionlessProjectKey, VersionlessProjectKey>> byFile = new LinkedHashMap<File, Map<VersionlessProjectKey, VersionlessProjectKey>>();

    private VersionlessProjectKey toCoord( final String src )
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

        return new VersionlessProjectKey( parts[0], parts[1] );
    }

    public ProjectKey getRelocation( final String groupId, final String artifactId )
    {
        return getRelocation( new VersionlessProjectKey( groupId, artifactId ) );
    }

    public ProjectKey getRelocation( final ProjectKey key )
    {
        return relocations.get( key );
    }

    public Relocations addBomRelocations( final File bom, final String relocationsStr )
        throws VManException
    {
        final String[] lines = relocationsStr.split( "[\\s*,\\s*]+" );
        if ( lines != null && lines.length > 0 )
        {
            LOGGER.info( bom + ": Found " + lines.length + " relocations..." );
            final Map<VersionlessProjectKey, VersionlessProjectKey> relocations = new LinkedHashMap<VersionlessProjectKey, VersionlessProjectKey>();
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
                    final VersionlessProjectKey key = toCoord( line.substring( 0, idx ).trim() );
                    final VersionlessProjectKey val = toCoord( line.substring( idx + 1 ).trim() );

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

    public Map<File, Map<VersionlessProjectKey, VersionlessProjectKey>> getRelocationsByFile()
    {
        return byFile;
    }

}
