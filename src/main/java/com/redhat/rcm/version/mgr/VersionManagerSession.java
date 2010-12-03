/*
 * Copyright (c) 2010 Red Hat, Inc.
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

package com.redhat.rcm.version.mgr;

import org.apache.maven.model.Dependency;

import com.redhat.rcm.version.util.ActivityLog;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class VersionManagerSession
{

    private final Map<String, Set<File>> missingVersions = new HashMap<String, Set<File>>();

    private final Map<File, Throwable> errors = new LinkedHashMap<File, Throwable>();

    private final Map<File, ActivityLog> logs = new LinkedHashMap<File, ActivityLog>();

    private final Map<String, String> depMap = new HashMap<String, String>();

    private final Map<File, Map<String, String>> bomDepMap = new HashMap<File, Map<String, String>>();

    private final File backups;

    private final boolean preserveDirs;

    public VersionManagerSession( final File backups, final boolean preserveDirs )
    {
        this.backups = backups;
        this.preserveDirs = preserveDirs;
    }

    public Map<File, ActivityLog> getLogs()
    {
        return logs;
    }

    public synchronized ActivityLog getLog( final File pom )
    {
        ActivityLog log = logs.get( pom );
        if ( log == null )
        {
            log = new ActivityLog();
            logs.put( pom, log );
        }

        return log;
    }

    public synchronized VersionManagerSession addMissingVersion( final File pom, final String key )
    {
        Set<File> poms = missingVersions.get( key );
        if ( poms == null )
        {
            poms = new HashSet<File>();
            missingVersions.put( key, poms );
        }

        poms.add( pom );

        return this;
    }

    public VersionManagerSession setError( final File pom, final Throwable error )
    {
        errors.put( pom, error );
        return this;
    }

    public VersionManagerSession mapDependency( final File srcBom, final Dependency dep )
    {
        final String pomKey = dep.getGroupId() + ":" + dep.getArtifactId() + ":pom";
        final String key = dep.getManagementKey();
        final String version = dep.getVersion();

        if ( !depMap.containsKey( key ) )
        {
            depMap.put( pomKey, version );
            depMap.put( key, version );
        }

        Map<String, String> bomMap = bomDepMap.get( srcBom );
        if ( bomMap == null )
        {
            bomMap = new HashMap<String, String>();
            bomDepMap.put( srcBom, bomMap );
        }

        bomMap.put( pomKey, version );
        bomMap.put( key, version );

        return this;
    }

    public VersionManagerSession startBomMap( final File srcBom, final String bomKey, final String version )
    {
        depMap.put( bomKey, version );

        Map<String, String> bomMap = bomDepMap.get( srcBom );
        if ( bomMap == null )
        {
            bomMap = new HashMap<String, String>();
            bomDepMap.put( srcBom, bomMap );
        }

        bomMap.put( bomKey, version );

        return this;
    }

    public Map<String, String> getDependencyMap()
    {
        return depMap;
    }

    public File getBackups()
    {
        return backups;
    }

    public boolean isPreserveDirs()
    {
        return preserveDirs;
    }

    public Map<String, Set<File>> getMissingVersions()
    {
        return missingVersions;
    }

    public Map<File, Throwable> getErrors()
    {
        return errors;
    }

    public boolean hasDependencyMap()
    {
        return !depMap.isEmpty();
    }

    public Map<File, Map<String, String>> getMappedDependenciesByBom()
    {
        return bomDepMap;
    }

}
