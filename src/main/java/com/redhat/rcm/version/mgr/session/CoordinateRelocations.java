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

package com.redhat.rcm.version.mgr.session;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.commonjava.util.logging.Logger;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;

import com.redhat.rcm.version.VManException;

public class CoordinateRelocations
{

    private final Logger logger = new Logger( getClass() );

    private final Map<VersionlessProjectKey, FullProjectKey> relocatedCoords =
        new HashMap<VersionlessProjectKey, FullProjectKey>();

    private final Map<File, Map<VersionlessProjectKey, FullProjectKey>> byFile =
        new LinkedHashMap<File, Map<VersionlessProjectKey, FullProjectKey>>();

    private final VersionManagerSession session;

    public CoordinateRelocations( final Map<String, String> relocations, final VersionManagerSession session )
    {
        this.session = session;
        addRelocations( relocations );
    }

    private VersionlessProjectKey toVersionlessCoord( final String src )
        throws VManException
    {
        final String[] parts = src.split( ":" );
        if ( parts.length < 2 )
        {
            throw new VManException( "Invalid coordinate: '" + src + "'." );
        }

        parts[0] = parts[0].trim();
        parts[1] = parts[1].trim();
        if ( parts[0].length() < 1 || parts[1].length() < 1 )
        {
            throw new VManException( "Invalid coordinate: '" + src + "'." );
        }

        final VersionlessProjectKey key = new VersionlessProjectKey( parts[0], parts[1] );

        if ( parts.length > 2 )
        {
            logger.warn( "Ignoring relocation key parts: '" + src.substring( key.getId()
                                                                                .length() ) + "' for: '" + key + "'." );
        }

        return key;
    }

    private FullProjectKey toFullCoord( final String src )
        throws VManException
    {
        final String[] parts = src.split( ":" );
        if ( parts.length < 3 )
        {
            throw new VManException( "Invalid coordinate: '" + src + "'." );
        }

        parts[0] = parts[0].trim();
        parts[1] = parts[1].trim();
        parts[2] = parts[2].trim();
        if ( parts[0].length() < 1 || parts[1].length() < 1 || parts[2].length() < 1 )
        {
            throw new VManException( "Invalid coordinate: '" + src + "'." );
        }

        final FullProjectKey key = new FullProjectKey( parts[0], parts[1], parts[2] );

        if ( parts.length > 3 )
        {
            logger.warn( "Ignoring relocation value parts: '" + src.substring( key.getId()
                                                                                  .length() ) + "' for: '" + key + "'." );
        }

        return key;
    }

    public FullProjectKey getRelocation( final ProjectKey key )
    {
        final FullProjectKey result = relocatedCoords.get( new VersionlessProjectKey( key ) );
        return result;
    }

    public CoordinateRelocations addBomRelocations( final File bom, final Map<String, String> relocations )
    {
        final Map<VersionlessProjectKey, FullProjectKey> relocatedCoords = addRelocations( relocations );

        byFile.put( bom, relocatedCoords );

        return this;
    }

    private Map<VersionlessProjectKey, FullProjectKey> addRelocations( final Map<String, String> relocations )
    {
        final Map<VersionlessProjectKey, FullProjectKey> relocatedCoords =
            new HashMap<VersionlessProjectKey, FullProjectKey>();

        if ( relocations != null )
        {
            for ( final Map.Entry<String, String> entry : relocations.entrySet() )
            {
                logger.info( "Processing relocation of: '" + entry.getKey() + "' to: '" + entry.getValue() + "'." );
                try
                {
                    final VersionlessProjectKey key = toVersionlessCoord( entry.getKey() );
                    final FullProjectKey val = toFullCoord( entry.getValue() );

                    logger.info( "Adding relocation from: " + key + " to: " + val );
                    relocatedCoords.put( key, val );
                }
                catch ( final VManException e )
                {
                    logger.warn( "NOT adding relocation from: '" + entry.getKey() + "' to: '" + entry.getValue()
                        + "'. Error: " + e.getMessage() );
                    session.addError( e );
                }

            }
        }
        this.relocatedCoords.putAll( relocatedCoords );

        return relocatedCoords;
    }

    public Map<File, Map<VersionlessProjectKey, FullProjectKey>> getRelocationsByFile()
    {
        return byFile;
    }

}
