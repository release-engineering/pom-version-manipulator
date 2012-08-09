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

import static com.redhat.rcm.version.util.InputUtils.parseProperties;

import org.apache.log4j.Logger;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.project.MavenProject;

import com.redhat.rcm.version.Cli;
import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.model.Project;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

class ManagedInfo
{

    private static final Logger LOGGER = Logger.getLogger( VersionManagerSession.class );

    private static final String RELOCATIONS_KEY = "relocations";

    private static final String MAPPINGS_KEY = "mapping";

    private final Set<FullProjectKey> bomCoords = new LinkedHashSet<FullProjectKey>();

    private final CoordinateRelocations relocatedCoords;

    private final PropertyMappings propertyMappings;

    private final Map<VersionlessProjectKey, String> depMap = new HashMap<VersionlessProjectKey, String>();

    private final Map<File, Map<VersionlessProjectKey, String>> bomDepMap =
        new HashMap<File, Map<VersionlessProjectKey, String>>();

    private final Map<VersionlessProjectKey, Plugin> managedPlugins =
        new LinkedHashMap<VersionlessProjectKey, Plugin>();

    private final Map<VersionlessProjectKey, Plugin> injectedPlugins =
        new LinkedHashMap<VersionlessProjectKey, Plugin>();

    private FullProjectKey toolchainKey;

    private final Set<VersionlessProjectKey> removedPlugins = new HashSet<VersionlessProjectKey>();

    private final Set<Project> currentProjects = new LinkedHashSet<Project>();

    private final Set<VersionlessProjectKey> currentProjectKeys = new LinkedHashSet<VersionlessProjectKey>();

    private final Set<String> modderKeys = new HashSet<String>();

    ManagedInfo( final VersionManagerSession session, final Collection<String> removedPlugins,
                 final Set<String> modderKeys, final Map<String, String> relocatedCoords,
                 final Map<String, String> propertyMappings )
    {
        this.relocatedCoords = new CoordinateRelocations( relocatedCoords, session );
        this.propertyMappings = new PropertyMappings( propertyMappings, session );

        setRemovedPlugins( removedPlugins );
        setModderKeys( modderKeys );
    }

    boolean hasDependencyMap()
    {
        return !depMap.isEmpty();
    }

    String getArtifactVersion( final ProjectKey key )
    {
        return depMap.get( key );
    }

    Map<File, Map<VersionlessProjectKey, String>> getMappedDependenciesByBom()
    {
        return bomDepMap;
    }

    void mapDependency( final File srcBom, final Dependency dep )
    {
        final VersionlessProjectKey key = new VersionlessProjectKey( dep.getGroupId(), dep.getArtifactId() );
        final String version = dep.getVersion();

        final String lastVersion = depMap.put( key, version );

        Map<VersionlessProjectKey, String> bomMap = bomDepMap.get( srcBom );
        if ( bomMap == null )
        {
            bomMap = new HashMap<VersionlessProjectKey, String>();
            bomDepMap.put( srcBom, bomMap );
        }

        bomMap.put( key, version );

        // remove this key from all other BOM mappings if it overwrites something from an earlier BOM.
        if ( lastVersion != null )
        {
            for ( final Map<VersionlessProjectKey, String> map : bomDepMap.values() )
            {
                if ( map == bomMap )
                {
                    continue;
                }

                if ( map.containsKey( key ) )
                {
                    map.remove( key );
                }
            }
        }
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

    void addBOM( final File bom, final MavenProject project )
        throws VManException
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
                LOGGER.warn( "[DEPRECATED] BOM-based coordinate relocations have been replaced by the "
                    + Cli.RELOCATIONS_PROPERTY
                    + " configuration, which specifies a URL to a properties file. Please use this instead." );

                relocatedCoords.addBomRelocations( bom, parseProperties( relocations ) );
            }

            final String mappings = properties.getProperty( MAPPINGS_KEY );
            LOGGER.info( "Got mappings:\n\n" + mappings );
            if ( mappings != null )
            {
                LOGGER.warn( "[DEPRECATED] BOM-based property mappings have been replaced by the "
                    + Cli.PROPERTY_MAPPINGS_PROPERTY
                    + " configuration, which specifies a URL to a properties file. Please use this instead." );

                propertyMappings.addBomPropertyMappings( bom, parseProperties( mappings ) );
            }
        }
    }

    FullProjectKey getRelocation( final ProjectKey key )
    {
        return relocatedCoords.getRelocation( key );
    }

    Set<FullProjectKey> getBomCoords()
    {
        return bomCoords;
    }

    CoordinateRelocations getRelocations()
    {
        return relocatedCoords;
    }

    void setToolchain( final File toolchainFile, final MavenProject project )
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
    }

    void setRemovedPlugins( final Collection<String> removedPlugins )
    {
        if ( removedPlugins == null )
        {
            return;
        }

        for ( final String rm : removedPlugins )
        {
            this.removedPlugins.add( new VersionlessProjectKey( rm ) );
        }
    }

    Set<VersionlessProjectKey> getRemovedPlugins()
    {
        return removedPlugins;
    }

    void setModderKeys( final Set<String> modderKeys )
    {
        if ( modderKeys == null )
        {
            return;
        }

        for ( final String key : modderKeys )
        {
            this.modderKeys.add( key );
        }
    }

    Set<String> getModderKeys()
    {
        return modderKeys;
    }

    public FullProjectKey getToolchainKey()
    {
        return toolchainKey;
    }

    Plugin getManagedPlugin( final VersionlessProjectKey key )
    {
        return managedPlugins.get( key );
    }

    Map<VersionlessProjectKey, Plugin> getInjectedPlugins()
    {
        return injectedPlugins;
    }

    boolean hasBom( final FullProjectKey key )
    {
        return bomCoords.contains( key );
    }

    boolean isToolchainReference( final Parent parent )
    {
        return toolchainKey == null ? false
                        : new VersionlessProjectKey( toolchainKey ).equals( new VersionlessProjectKey( parent ) );
    }

    public synchronized void setCurrentProjects( final Collection<Model> models )
        throws ProjectToolsException
    {
        if ( models == null || models.isEmpty() )
        {
            return;
        }

        currentProjects.clear();
        currentProjectKeys.clear();
        for ( final Model model : new LinkedHashSet<Model>( models ) )
        {
            final Project project = new Project( model );
            currentProjects.add( project );
            currentProjectKeys.add( new VersionlessProjectKey( project.getKey() ) );
        }
    }

    public Set<Project> getCurrentProjects()
    {
        return currentProjects;
    }

    public boolean isCurrentProject( final ProjectKey key )
    {
        return currentProjectKeys.contains( new VersionlessProjectKey( key ) );
    }

    public PropertyMappings getPropertyMapping()
    {
        return propertyMappings;
    }

}
