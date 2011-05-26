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

package com.redhat.rcm.version.mgr;

import org.apache.log4j.Logger;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.model.FullProjectKey;
import com.redhat.rcm.version.model.ProjectKey;
import com.redhat.rcm.version.model.Relocations;
import com.redhat.rcm.version.model.VersionlessProjectKey;
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

    private final Map<VersionlessProjectKey, Set<File>> missingVersions =
        new HashMap<VersionlessProjectKey, Set<File>>();

    private final Map<File, Set<Throwable>> errors = new LinkedHashMap<File, Set<Throwable>>();

    private final Map<File, ActivityLog> logs = new LinkedHashMap<File, ActivityLog>();

    private final Map<VersionlessProjectKey, String> depMap = new HashMap<VersionlessProjectKey, String>();

    private final Map<File, Map<VersionlessProjectKey, String>> bomDepMap =
        new HashMap<File, Map<VersionlessProjectKey, String>>();

    private final Set<FullProjectKey> bomCoords = new LinkedHashSet<FullProjectKey>();

    private final Relocations relocations = new Relocations();

    private final File backups;
    
    private final File downloads;

    private final boolean preserveFiles;

    private final boolean normalizeBomUsage;

    public VersionManagerSession( final File workspace, final boolean preserveFiles, final boolean normalizeBomUsage )
    {
        this.backups = new File( workspace, "backups" );
        this.backups.mkdirs();
        
        this.downloads = new File( workspace, "downloads" );
        this.downloads.mkdirs();
        
        this.preserveFiles = preserveFiles;
        this.normalizeBomUsage = normalizeBomUsage;
    }

    public ProjectKey getRelocation( final String groupId, final String artifactId )
    {
        return relocations.getRelocation( groupId, artifactId );
    }

    public ProjectKey getRelocation( final ProjectKey key )
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

    public synchronized VersionManagerSession addMissingVersion( final File pom, final VersionlessProjectKey key )
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
    
    public File getDownloads()
    {
        return downloads;
    }

    public boolean isPreserveFiles()
    {
        return preserveFiles;
    }

    public Map<VersionlessProjectKey, Set<File>> getMissingVersions()
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

    public String getArtifactVersion( final ProjectKey key )
    {
        return depMap.get( key );
    }

    public Map<File, Map<VersionlessProjectKey, String>> getMappedDependenciesByBom()
    {
        return bomDepMap;
    }

    public Set<FullProjectKey> getBomCoords()
    {
        return bomCoords;
    }

    public VersionManagerSession addBOM( final File bom, final MavenProject project )
    {
        bomCoords.add( new FullProjectKey( project.getGroupId(), project.getArtifactId(), project.getVersion() ) );

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
        final VersionlessProjectKey key = new VersionlessProjectKey( dep.getGroupId(), dep.getArtifactId() );
        final String version = dep.getVersion();

        if ( !depMap.containsKey( key ) )
        {
            depMap.put( key, version );
        }

        Map<VersionlessProjectKey, String> bomMap = bomDepMap.get( srcBom );
        if ( bomMap == null )
        {
            bomMap = new HashMap<VersionlessProjectKey, String>();
            bomDepMap.put( srcBom, bomMap );
        }

        bomMap.put( key, version );
    }

    private void startBomMap( final File srcBom, final String groupId, final String artifactId, final String version )
    {
        final VersionlessProjectKey bomKey = new VersionlessProjectKey( groupId, artifactId );
        depMap.put( bomKey, version );

        Map<VersionlessProjectKey, String> bomMap = bomDepMap.get( srcBom );
        if ( bomMap == null )
        {
            bomMap = new HashMap<VersionlessProjectKey, String>();
            bomDepMap.put( srcBom, bomMap );
        }

        bomMap.put( bomKey, version );
    }

    public Relocations getRelocations()
    {
        return relocations;
    }

    public boolean isNormalizeBomUsage()
    {
        return normalizeBomUsage;
    }

}
