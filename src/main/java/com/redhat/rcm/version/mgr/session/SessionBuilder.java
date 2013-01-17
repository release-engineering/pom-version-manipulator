/*
 *  Copyright (c) 2012 Red Hat, Inc.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.commonjava.util.logging.Logger;

import com.redhat.rcm.version.mgr.mod.ProjectModder;

public final class SessionBuilder
{
    public static final List<String> STANDARD =
        new ArrayList<String>( Arrays.asList( ProjectModder.STANDARD_MODIFICATIONS ) );

    private final Logger logger = new Logger( getClass() );

    private final File workspace;

    private final File reports;

    private File localRepo;

    private String versionSuffix = "-rebuild-1";

    private String versionModifier = ":";

    private Collection<String> removedPlugins = new HashSet<String>();

    private Collection<String> removedTests = new HashSet<String>();

    private Collection<String> extensionsWhitelist = new HashSet<String>();

    private final List<String> modders = new ArrayList<String>( STANDARD );

    private boolean preserveFiles = false;

    private boolean strict = true;

    private final Map<String, String> coordinateRelocations = new HashMap<String, String>();

    private final Map<String, String> propertyMappings = new HashMap<String, String>();

    private final Set<VersionlessProjectKey> excludedModulePoms = new HashSet<VersionlessProjectKey>();

    public SessionBuilder( final File workspace )
    {
        this.workspace = workspace;
        reports = new File( workspace, "reports" );
        reports.mkdirs();
    }

    public SessionBuilder( final File workspace, final File reports )
    {
        this.workspace = workspace;
        this.reports = reports;
    }

    public VersionManagerSession build()
    {
        final VersionManagerSession sess =
            new VersionManagerSession( workspace, reports, versionSuffix, versionModifier, removedPlugins,
                                       removedTests, extensionsWhitelist, modders, preserveFiles, strict,
                                       coordinateRelocations, propertyMappings, excludedModulePoms );

        sess.setLocalRepositoryDirectory( localRepo == null ? new File( workspace, "local-repository" ) : localRepo );

        return sess;
    }

    public SessionBuilder withExcludedModulePoms( final String excludedModulePoms )
    {
        this.excludedModulePoms.clear();
        if ( excludedModulePoms == null )
        {
            return this;
        }

        final String[] entries = excludedModulePoms == null ? new String[] {} : excludedModulePoms.split( "," );
        for ( String entry : entries )
        {
            entry = entry.trim();
            if ( entry.length() < 3 )
            {
                continue;
            }

            try
            {
                final VersionlessProjectKey key = new VersionlessProjectKey( entry );
                this.excludedModulePoms.add( key );
            }
            catch ( final IllegalArgumentException e )
            {
                logger.error( "Cannot parse excluded-module pom entry: '%s'. Reason: %s", e, entry, e.getMessage() );
            }
        }

        return this;
    }

    public SessionBuilder withExcludedModulePoms( final VersionlessProjectKey... keys )
    {
        this.excludedModulePoms.clear();
        this.excludedModulePoms.addAll( Arrays.asList( keys ) );
        return this;
    }

    public SessionBuilder withExcludedModulePoms( final Collection<VersionlessProjectKey> keys )
    {
        this.excludedModulePoms.clear();
        if ( keys == null )
        {
            return this;
        }

        this.excludedModulePoms.addAll( keys );
        return this;
    }

    public SessionBuilder withLocalRepositoryDirectory( final File localRepo )
    {
        this.localRepo = localRepo;
        return this;
    }

    public SessionBuilder withVersionSuffix( final String versionSuffix )
    {
        this.versionSuffix = versionSuffix;
        return this;
    }

    public SessionBuilder withVersionModifier( final String versionModifier )
    {
        this.versionModifier = versionModifier;
        return this;
    }

    public SessionBuilder withRemovedPlugins( final Collection<String> removedPlugins )
    {
        this.removedPlugins = removedPlugins;
        return this;
    }

    public SessionBuilder withRemovedTests( final Collection<String> removedTests )
    {
        this.removedTests = removedTests;
        return this;
    }

    public SessionBuilder withExtensionsWhitelist( final Collection<String> extensionsWhitelist )
    {
        this.extensionsWhitelist = extensionsWhitelist;
        return this;
    }

    public SessionBuilder withStrict( final boolean strict )
    {
        this.strict = strict;
        return this;
    }

    public SessionBuilder withPropertyMapping( final String key, final String value )
    {
        propertyMappings.put( key, value );

        return this;
    }

    public SessionBuilder withCoordinateRelocation( final String oldCoord, final String newCoord )
    {
        coordinateRelocations.put( oldCoord, newCoord );
        return this;
    }

    public SessionBuilder withModders( final List<String> modders )
    {
        if ( modders == null )
        {
            return this;
        }

        this.modders.clear();
        this.modders.addAll( modders );
        return this;
    }

    public SessionBuilder withPreserveFiles( final boolean preserveFiles )
    {
        this.preserveFiles = preserveFiles;
        return this;
    }

    public SessionBuilder withCoordinateRelocations( final Map<String, String> coordinateRelocations )
    {
        this.coordinateRelocations.clear();
        if ( coordinateRelocations == null )
        {
            return this;
        }

        this.coordinateRelocations.putAll( coordinateRelocations );
        return this;
    }

    public SessionBuilder withPropertyMappings( final Map<String, String> propertyMappings )
    {
        this.propertyMappings.clear();
        if ( propertyMappings == null )
        {
            return this;
        }

        this.propertyMappings.putAll( propertyMappings );
        return this;
    }

}