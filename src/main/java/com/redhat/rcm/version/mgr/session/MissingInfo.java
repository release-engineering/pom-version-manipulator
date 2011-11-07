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

package com.redhat.rcm.version.mgr.session;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;

import com.redhat.rcm.version.model.ReadOnlyDependency;
import com.redhat.rcm.version.model.Project;

class MissingInfo
{
    private final Set<Project> missingParents = new HashSet<Project>();

    private final Map<VersionlessProjectKey, Set<File>> missingVersions =
        new HashMap<VersionlessProjectKey, Set<File>>();

    private final Map<VersionlessProjectKey, Set<VersionlessProjectKey>> missingVersionsByProject =
        new HashMap<VersionlessProjectKey, Set<VersionlessProjectKey>>();

    private final Map<VersionlessProjectKey, Set<Dependency>> missingDeps =
        new HashMap<VersionlessProjectKey, Set<Dependency>>();

    private final Map<File, Set<VersionlessProjectKey>> unmanagedPlugins =
        new HashMap<File, Set<VersionlessProjectKey>>();

    private final Map<VersionlessProjectKey, Set<Plugin>> unmanagedPluginRefs =
        new HashMap<VersionlessProjectKey, Set<Plugin>>();

    void addUnmanagedPlugin( final File pom, final Plugin plugin )
    {
        VersionlessProjectKey pluginKey = new VersionlessProjectKey( plugin );

        Set<VersionlessProjectKey> pluginKeys = unmanagedPlugins.get( pom );
        if ( pluginKeys == null )
        {
            pluginKeys = new HashSet<VersionlessProjectKey>();
            unmanagedPlugins.put( pom, pluginKeys );
        }

        pluginKeys.add( pluginKey );

        Set<Plugin> plugins = unmanagedPluginRefs.get( pluginKey );
        if ( plugins == null )
        {
            plugins = new HashSet<Plugin>();
            unmanagedPluginRefs.put( pluginKey, plugins );
        }

        plugins.add( plugin );
    }

    void addMissingParent( final Project project )
    {
        missingParents.add( project );
    }

    void addMissingDependency( final Project project, final Dependency dep )
    {
        VersionlessProjectKey depKey = new VersionlessProjectKey( dep );

        Set<File> poms = missingVersions.get( depKey );
        if ( poms == null )
        {
            poms = new HashSet<File>();
            missingVersions.put( depKey, poms );
        }

        poms.add( project.getPom() );

        VersionlessProjectKey vpk = new VersionlessProjectKey( project.getKey() );
        Set<VersionlessProjectKey> keys = missingVersionsByProject.get( vpk );
        if ( keys == null )
        {
            keys = new HashSet<VersionlessProjectKey>();
            missingVersionsByProject.put( vpk, keys );
        }
        keys.add( depKey );

        Set<Dependency> deps = missingDeps.get( depKey );
        if ( deps == null )
        {
            deps = new HashSet<Dependency>();
            missingDeps.put( depKey, deps );
        }

        deps.add( new ReadOnlyDependency( dep ) );
    }

    Map<VersionlessProjectKey, Set<Plugin>> getUnmanagedPluginRefs()
    {
        return unmanagedPluginRefs;
    }

    Map<File, Set<VersionlessProjectKey>> getUnmanagedPlugins()
    {
        return unmanagedPlugins;
    }

    Set<VersionlessProjectKey> getUnmanagedPlugins( final File pom )
    {
        return unmanagedPlugins.get( pom );
    }

    Set<Project> getProjectsWithMissingParent()
    {
        return missingParents;
    }

    boolean isMissingParent( final Project project )
    {
        return missingParents.contains( project );
    }

    Map<VersionlessProjectKey, Set<Dependency>> getMissingDependencies()
    {
        return missingDeps;
    }

    Set<Dependency> getMissingDependencies( final VersionlessProjectKey key )
    {
        return missingDeps.get( key );
    }

    Map<VersionlessProjectKey, Set<File>> getMissingVersions()
    {
        return missingVersions;
    }

    Set<VersionlessProjectKey> getMissingVersions( final ProjectKey key )
    {
        return missingVersionsByProject.get( new VersionlessProjectKey( key ) );
    }
}
