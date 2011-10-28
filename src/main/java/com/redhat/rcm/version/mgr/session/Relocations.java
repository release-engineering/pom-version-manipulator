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

import org.apache.log4j.Logger;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;

import com.redhat.rcm.version.VManException;

public class Relocations
{

    private static final Logger LOGGER = Logger.getLogger( Relocations.class );

    private final Map<VersionlessProjectKey, FullProjectKey> relocations =
        new HashMap<VersionlessProjectKey, FullProjectKey>();

    private final Map<File, Map<VersionlessProjectKey, FullProjectKey>> byFile =
        new LinkedHashMap<File, Map<VersionlessProjectKey, FullProjectKey>>();

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

        VersionlessProjectKey key = new VersionlessProjectKey( parts[0], parts[1] );

        if ( parts.length > 2 )
        {
            LOGGER.warn( "Ignoring relocation key parts: '" + src.substring( key.getId().length() ) + "' for: '" + key
                + "'." );
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

        FullProjectKey key = new FullProjectKey( parts[0], parts[1], parts[2] );

        if ( parts.length > 3 )
        {
            LOGGER.warn( "Ignoring relocation value parts: '" + src.substring( key.getId().length() ) + "' for: '"
                + key + "'." );
        }

        return key;
    }

    public FullProjectKey getRelocation( final ProjectKey key )
    {
        return relocations.get( new VersionlessProjectKey( key ) );
    }

    public Relocations addBomRelocations( final File bom, final String relocationsStr,
                                          final VersionManagerSession session )
    {
        final String[] lines = relocationsStr.split( "[\\s*,\\s*]+" );
        if ( lines != null && lines.length > 0 )
        {
            LOGGER.info( bom + ": Found " + lines.length + " relocations..." );
            final Map<VersionlessProjectKey, FullProjectKey> relocations =
                new LinkedHashMap<VersionlessProjectKey, FullProjectKey>();

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
                    try
                    {
                        final VersionlessProjectKey key = toVersionlessCoord( line.substring( 0, idx ).trim() );
                        final FullProjectKey val = toFullCoord( line.substring( idx + 1 ).trim() );

                        LOGGER.info( "Adding relocation from: " + key + " to: " + val + " in BOM: " + bom );
                        relocations.put( key, val );
                    }
                    catch ( VManException e )
                    {
                        LOGGER.warn( "NOT adding relocation from line: '" + line + "'. Error: " + e.getMessage() );
                        session.addError( e );
                    }
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

    public Map<File, Map<VersionlessProjectKey, FullProjectKey>> getRelocationsByFile()
    {
        return byFile;
    }

}
