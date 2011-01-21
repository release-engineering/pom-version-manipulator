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

import org.apache.log4j.Logger;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;

import com.redhat.rcm.version.Coord;
import com.redhat.rcm.version.Relocations;
import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.util.ActivityLog;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class VersionManagerSession
{

    private static final Logger LOGGER = Logger.getLogger( VersionManagerSession.class );

    public static final File GLOBAL = new File( "/" );

    private static final String RELOCATIONS_KEY = "relocations";

    private final Map<Coord, Set<File>> missingVersions = new HashMap<Coord, Set<File>>();

    private final Map<File, Set<Throwable>> errors = new LinkedHashMap<File, Set<Throwable>>();

    private final Map<File, ActivityLog> logs = new LinkedHashMap<File, ActivityLog>();

    private final Map<Coord, String> depMap = new HashMap<Coord, String>();

    private final Map<File, Map<Coord, String>> bomDepMap = new HashMap<File, Map<Coord, String>>();

    private final Relocations relocations = new Relocations();

    private final File backups;

    private final boolean preserveFiles;

    public VersionManagerSession( final File backups, final boolean preserveFiles )
    {
        this.backups = backups;
        this.preserveFiles = preserveFiles;
    }

    public Coord getRelocation( final String groupId, final String artifactId )
    {
        return relocations.getRelocation( groupId, artifactId );
    }

    public Coord getRelocation( final Coord key )
    {
        return relocations.getRelocation( key );
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

    public synchronized VersionManagerSession addMissingVersion( final File pom, final Coord key )
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

    public VersionManagerSession addGlobalError( final Throwable error )
    {
        getErrors( GLOBAL, true ).add( error );
        return this;
    }

    public synchronized Set<Throwable> getErrors( final File file, final boolean create )
    {
        Set<Throwable> errors = this.errors.get( GLOBAL );
        if ( create && errors == null )
        {
            errors = new LinkedHashSet<Throwable>();
            this.errors.put( GLOBAL, errors );
        }

        return errors;
    }

    public VersionManagerSession addError( final File pom, final Throwable error )
    {
        getErrors( pom, true ).add( error );
        return this;
    }

    public File getBackups()
    {
        return backups;
    }

    public boolean isPreserveFiles()
    {
        return preserveFiles;
    }

    public Map<Coord, Set<File>> getMissingVersions()
    {
        return missingVersions;
    }

    public Map<File, Set<Throwable>> getErrors()
    {
        return errors;
    }

    public boolean hasDependencyMap()
    {
        return !depMap.isEmpty();
    }

    public String getArtifactVersion( final Coord key )
    {
        return depMap.get( key );
    }

    public Map<File, Map<Coord, String>> getMappedDependenciesByBom()
    {
        return bomDepMap;
    }

    public VersionManagerSession addBOM( final File bom, final MavenProject project )
    {
        startBomMap( bom, project.getGroupId(), project.getArtifactId(), project.getVersion() );

        if ( project.getDependencyManagement() != null && project.getDependencyManagement().getDependencies() != null )
        {
            for ( final Dependency dep : project.getDependencyManagement().getDependencies() )
            {
                mapDependency( bom, dep );
            }
        }

        final Properties properties = project.getProperties();
        if ( properties != null )
        {
            final String relocations = properties.getProperty( RELOCATIONS_KEY );
            LOGGER.info( "Got relocations:\n\n" + relocations );
            if ( relocations != null )
            {
                addRelocations( bom, relocations );
            }
        }

        return this;
    }

    private void addRelocations( final File bom, final String relocationsStr )
    {
        try
        {
            relocations.addBomRelocations( bom, relocationsStr );
        }
        catch ( final VManException e )
        {
            addGlobalError( e );
        }
    }

    public void mapDependency( final File srcBom, final Dependency dep )
    {
        final Coord key = new Coord( dep.getGroupId(), dep.getArtifactId() );
        final String version = dep.getVersion();

        if ( !depMap.containsKey( key ) )
        {
            depMap.put( key, version );
        }

        Map<Coord, String> bomMap = bomDepMap.get( srcBom );
        if ( bomMap == null )
        {
            bomMap = new HashMap<Coord, String>();
            bomDepMap.put( srcBom, bomMap );
        }

        bomMap.put( key, version );
    }

    private void startBomMap( final File srcBom, final String groupId, final String artifactId, final String version )
    {
        final Coord bomKey = new Coord( groupId, artifactId );
        depMap.put( bomKey, version );

        Map<Coord, String> bomMap = bomDepMap.get( srcBom );
        if ( bomMap == null )
        {
            bomMap = new HashMap<Coord, String>();
            bomDepMap.put( srcBom, bomMap );
        }

        bomMap.put( bomKey, version );
    }

    public Relocations getRelocations()
    {
        return relocations;
    }

}
