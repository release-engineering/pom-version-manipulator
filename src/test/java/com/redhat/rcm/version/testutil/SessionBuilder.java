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

package com.redhat.rcm.version.testutil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.redhat.rcm.version.mgr.mod.ProjectModder;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

public final class SessionBuilder
{
    public static final List<String> STANDARD =
        new ArrayList<String>( Arrays.asList( ProjectModder.STANDARD_MODIFICATIONS ) );

    private final File workspace;

    private final File reports;

    private String versionSuffix = "-rebuild-1";
    
    private String versionModifier = ":";

    private Collection<String> removedPlugins = new HashSet<String>();

    private Collection<String> removedTests = new HashSet<String>();

    private List<String> modders = STANDARD;

    private boolean preserveFiles = false;

    private boolean strict = true;

    private final Map<String, String> coordinateRelocations = new HashMap<String, String>();

    private final Map<String, String> propertyMappings = new HashMap<String, String>();

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
        return new VersionManagerSession( workspace, reports, versionSuffix, versionModifier, removedPlugins, removedTests, modders, preserveFiles,
                                          strict, coordinateRelocations, propertyMappings );
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

    public SessionBuilder withPreserveFiles( final boolean preserveFiles )
    {
        this.preserveFiles = preserveFiles;
        return this;
    }

    public SessionBuilder withStrict( final boolean strict )
    {
        this.strict = strict;
        return this;
    }

    public SessionBuilder withCoordinateRelocations( final Map<String, String> coordinateRelocations )
    {
        this.coordinateRelocations.putAll( coordinateRelocations );
        return this;
    }

    public SessionBuilder withPropertyMappings( final Map<String, String> propertyMappings )
    {
        this.propertyMappings.putAll( propertyMappings );
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

}