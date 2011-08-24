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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.mae.project.session.SimpleProjectToolsSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.project.MavenProject;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.RemoteRepository;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.model.ProjectAncestryGraph;
import com.redhat.rcm.version.model.Relocations;
import com.redhat.rcm.version.util.ActivityLog;

public class VersionManagerSession
    extends SimpleProjectToolsSession
{

    private static final Logger LOGGER = Logger.getLogger( VersionManagerSession.class );

    public static final File GLOBAL = new File( "/" );

    private static final String RELOCATIONS_KEY = "relocations";

    private final Map<VersionlessProjectKey, Set<File>> missingVersions =
        new HashMap<VersionlessProjectKey, Set<File>>();

    private final Map<File, Set<VersionlessProjectKey>> unmanagedPlugins =
        new HashMap<File, Set<VersionlessProjectKey>>();

    private final List<Throwable> errors = new ArrayList<Throwable>();

    private final Map<File, ActivityLog> logs = new LinkedHashMap<File, ActivityLog>();

    private final Map<VersionlessProjectKey, String> depMap = new HashMap<VersionlessProjectKey, String>();

    private final Map<File, Map<VersionlessProjectKey, String>> bomDepMap =
        new HashMap<File, Map<VersionlessProjectKey, String>>();

    private final Map<VersionlessProjectKey, Plugin> managedPlugins =
        new LinkedHashMap<VersionlessProjectKey, Plugin>();

    private final Map<VersionlessProjectKey, Plugin> injectedPlugins =
        new LinkedHashMap<VersionlessProjectKey, Plugin>();

    private final Set<VersionlessProjectKey> removedPlugins = new HashSet<VersionlessProjectKey>();

    private final Map<VersionlessProjectKey, Set<VersionlessProjectKey>> accumulatedPluginRefs =
        new HashMap<VersionlessProjectKey, Set<VersionlessProjectKey>>();

    private final Set<FullProjectKey> bomCoords = new LinkedHashSet<FullProjectKey>();

    private final Set<VersionlessProjectKey> currentProjects = new HashSet<VersionlessProjectKey>();

    private final Relocations relocations = new Relocations();

    private final File backups;

    private final File downloads;

    private final boolean preserveFiles;

    private final File workspace;

    private final File reports;

    private ProjectAncestryGraph ancestryGraph;

    private FullProjectKey toolchainKey;

    private final String versionSuffix;

    public VersionManagerSession( final File workspace, final File reports, final String versionSuffix,
                                  final boolean preserveFiles )
    {
        this.workspace = workspace;
        this.reports = reports;
        this.versionSuffix = versionSuffix;

        backups = new File( workspace, "backups" );
        backups.mkdirs();

        downloads = new File( workspace, "downloads" );
        downloads.mkdirs();

        this.preserveFiles = preserveFiles;
    }

    public String getVersionSuffix()
    {
        return versionSuffix;
    }

    public FullProjectKey getRelocation( final ProjectKey key )
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

    public synchronized VersionManagerSession addUnmanagedPlugin( final File pom, final VersionlessProjectKey pluginKey )
    {
        Set<VersionlessProjectKey> pluginKeys = unmanagedPlugins.get( pom );
        if ( pluginKeys == null )
        {
            pluginKeys = new HashSet<VersionlessProjectKey>();
            unmanagedPlugins.put( pom, pluginKeys );
        }

        pluginKeys.add( pluginKey );

        return this;
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

    public VersionManagerSession addError( final Throwable error )
    {
        errors.add( error );
        return this;
    }

    public File getWorkspace()
    {
        return workspace;
    }

    public File getReports()
    {
        return reports;
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

    public Map<File, Set<VersionlessProjectKey>> getUnmanagedPlugins()
    {
        return unmanagedPlugins;
    }

    public Map<VersionlessProjectKey, Set<File>> getMissingVersions()
    {
        return missingVersions;
    }

    public List<Throwable> getErrors()
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
            addError( e );
        }
    }

    public VersionManagerSession mapDependency( final File srcBom, final Dependency dep )
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

        return this;
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

    public VersionManagerSession setToolchain( final File toolchainFile, final MavenProject project )
    {
        toolchainKey = new FullProjectKey( project );

        final PluginManagement pm = project.getPluginManagement();
        if ( pm != null )
        {
            for ( final Plugin plugin : pm.getPlugins() )
            {
                managedPlugins.put( new VersionlessProjectKey( plugin ), plugin );
            }
        }

        final Model model = project.getOriginalModel();
        final Build build = model.getBuild();
        if ( build != null )
        {
            final List<Plugin> plugins = build.getPlugins();
            if ( plugins != null )
            {
                for ( final Plugin plugin : plugins )
                {
                    final VersionlessProjectKey key = new VersionlessProjectKey( plugin );
                    injectedPlugins.put( key, plugin );

                    if ( !managedPlugins.containsKey( key ) && plugin.getVersion() != null )
                    {
                        injectedPlugins.put( key, plugin );
                    }
                }
            }
        }

        return this;
    }

    public VersionManagerSession setRemovedPlugins( final Collection<String> removedPlugins )
    {
        for ( String rm : removedPlugins )
        {
            this.removedPlugins.add( new VersionlessProjectKey( rm ) );
        }

        return this;
    }

    public Set<VersionlessProjectKey> getRemovedPlugins()
    {
        return removedPlugins;
    }

    public FullProjectKey getToolchainKey()
    {
        return toolchainKey;
    }

    public Plugin getManagedPlugin( final VersionlessProjectKey key )
    {
        return managedPlugins.get( key );
    }

    public Map<VersionlessProjectKey, Plugin> getInjectedPlugins()
    {
        return injectedPlugins;
    }

    public Set<VersionlessProjectKey> getPluginReferences( final VersionlessProjectKey owner )
    {
        Set<VersionlessProjectKey> refs = accumulatedPluginRefs.get( owner );
        if ( refs == null )
        {
            refs = Collections.emptySet();
        }

        return refs;
    }

    public VersionManagerSession addPluginReference( final VersionlessProjectKey owner,
                                                     final VersionlessProjectKey plugin )
    {
        Set<VersionlessProjectKey> plugins = accumulatedPluginRefs.get( owner );
        if ( plugins == null )
        {
            plugins = new HashSet<VersionlessProjectKey>();
            accumulatedPluginRefs.put( owner, plugins );
        }

        plugins.add( plugin );

        return this;
    }

    public boolean isBom( final FullProjectKey key )
    {
        return bomCoords.contains( key );
    }

    // public boolean hasToolchainAncestor( final Project project )
    // {
    // return toolchainKey == null ? false : getAncestryGraph().hasAncestor( toolchainKey, project );
    // }
    //
    // public boolean hasParentInGraph( final Project project )
    // {
    // return getAncestryGraph().hasParentInGraph( project );
    // }

    public VersionManagerSession addProject( final Project project )
    {
        getAncestryGraph().connect( project );
        currentProjects.add( new VersionlessProjectKey( project.getKey() ) );

        return this;
    }

    private synchronized ProjectAncestryGraph getAncestryGraph()
    {
        if ( ancestryGraph == null )
        {
            ancestryGraph = new ProjectAncestryGraph( toolchainKey );
        }

        return ancestryGraph;
    }

    public boolean ancestryGraphContains( final FullProjectKey key )
    {
        return getAncestryGraph().contains( key );
    }

    public void setRemoteRepository( final String remoteRepository )
        throws MalformedURLException
    {
        String id = "vman";

        String u = remoteRepository;
        int idx = u.indexOf( '|' );
        if ( idx > 0 )
        {
            id = u.substring( 0, idx );
            u = u.substring( idx + 1 );
        }

        URL url = new URL( u );

        Authentication auth = null;

        String ui = url.getUserInfo();
        if ( ui != null )
        {
            idx = ui.indexOf( ':' );

            String user = ui;
            String password = null;

            if ( idx > 0 )
            {
                user = ui.substring( 0, idx );

                if ( idx + 1 < ui.length() )
                {
                    password = ui.substring( idx + 1 );
                }
            }

            auth = new Authentication( user, password );
        }

        RemoteRepository repo = new RemoteRepository( id, "default", u );
        if ( auth != null )
        {
            repo.setAuthentication( auth );
        }

        setRemoteRepositoriesForResolution( Collections.singletonList( repo ) );
    }

    public boolean isToolchainReference( final Parent parent )
    {
        return toolchainKey == null ? false
                        : new VersionlessProjectKey( toolchainKey ).equals( new VersionlessProjectKey( parent ) );
    }

    public boolean inCurrentSession( final Parent parent )
    {
        return currentProjects.contains( new VersionlessProjectKey( parent ) );
    }

}
