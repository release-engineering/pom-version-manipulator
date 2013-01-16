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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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
import org.commonjava.util.logging.Logger;

import com.redhat.rcm.version.Cli;
import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.maven.WildcardProjectKey;
import com.redhat.rcm.version.model.DependencyManagementKey;
import com.redhat.rcm.version.model.Project;

class ManagedInfo
{
    private final Logger logger = new Logger( getClass() );

    private static final String RELOCATIONS_KEY = "relocations";

    private static final String MAPPINGS_KEY = "mapping";

    private final Map<FullProjectKey, File> peekedPoms = new HashMap<FullProjectKey, File>();

    private final Map<File, FullProjectKey> peekedPomsReverse = new HashMap<File, FullProjectKey>();

    private final LinkedHashMap<FullProjectKey, MavenProject> bomProjects =
        new LinkedHashMap<FullProjectKey, MavenProject>();

    private final CoordinateRelocations relocatedCoords;

    private final PropertyMappings propertyMappings;

    private final Map<DependencyManagementKey, Dependency> depMap = new HashMap<DependencyManagementKey, Dependency>();

    private final Map<File, Map<DependencyManagementKey, String>> bomDepMap =
        new HashMap<File, Map<DependencyManagementKey, String>>();

    private final Map<VersionlessProjectKey, Plugin> managedPlugins =
        new LinkedHashMap<VersionlessProjectKey, Plugin>();

    private final Map<VersionlessProjectKey, Plugin> injectedPlugins =
        new LinkedHashMap<VersionlessProjectKey, Plugin>();

    private FullProjectKey toolchainKey;

    private final Set<VersionlessProjectKey> removedPlugins = new HashSet<VersionlessProjectKey>();

    private final List<WildcardProjectKey> removedTests = new ArrayList<WildcardProjectKey>();

    private final Set<VersionlessProjectKey> extensionsWhitelist = new HashSet<VersionlessProjectKey>();

    private final LinkedHashSet<Project> currentProjects = new LinkedHashSet<Project>();

    private final Map<VersionlessProjectKey, Project> currentProjectsByKey =
        new LinkedHashMap<VersionlessProjectKey, Project>();

    private final List<String> modderKeys = new ArrayList<String>();

    private MavenProject toolchainProject;

    private final Set<VersionlessProjectKey> excludedModulePoms;

    ManagedInfo( final VersionManagerSession session, final Collection<String> removedPlugins,
                 final Collection<String> removedTests, final Collection<String> extensionsWhitelist,
                 final List<String> modderKeys, final Map<String, String> relocatedCoords,
                 final Map<String, String> propertyMappings, final Set<VersionlessProjectKey> excludedModulePoms )
    {
        this.excludedModulePoms = excludedModulePoms;
        this.relocatedCoords = new CoordinateRelocations( relocatedCoords, session );
        this.propertyMappings = new PropertyMappings( propertyMappings, session );

        if ( removedPlugins != null )
        {
            for ( final String rm : removedPlugins )
            {
                this.removedPlugins.add( new VersionlessProjectKey( rm ) );
            }
        }
        if ( removedTests != null )
        {
            for ( final String rm : removedTests )
            {
                this.removedTests.add( new WildcardProjectKey( rm ) );
            }
        }
        if ( extensionsWhitelist != null )
        {
            for ( final String rm : extensionsWhitelist )
            {
                this.extensionsWhitelist.add( new VersionlessProjectKey( rm ) );
            }
        }
        if ( modderKeys != null )
        {
            for ( final String key : modderKeys )
            {
                this.modderKeys.add( key );
            }
        }
    }

    boolean hasDependencyMap()
    {
        return !depMap.isEmpty();
    }

    Dependency getManagedDependency( final DependencyManagementKey key )
    {
        return depMap.get( key );
    }

    Map<File, Map<DependencyManagementKey, String>> getMappedDependenciesByBom()
    {
        return bomDepMap;
    }

    void mapDependency( final File srcBom, final Dependency dep )
    {
        final DependencyManagementKey key = new DependencyManagementKey( dep );
        final String version = dep.getVersion();

        if ( !depMap.containsKey( key ) )
        {
            depMap.put( key, dep );

            Map<DependencyManagementKey, String> bomMap = bomDepMap.get( srcBom );
            if ( bomMap == null )
            {
                bomMap = new HashMap<DependencyManagementKey, String>();
                bomDepMap.put( srcBom, bomMap );
            }

            bomMap.put( key, version );
        }
    }

    private void startBomMap( final File srcBom, final String groupId, final String artifactId, final String version )
    {
        final DependencyManagementKey bomKey = new DependencyManagementKey( groupId, artifactId, "pom", null );

        final Dependency dep = new Dependency();
        dep.setGroupId( groupId );
        dep.setArtifactId( artifactId );
        dep.setVersion( version );
        dep.setScope( "import" );
        dep.setType( "pom" );

        depMap.put( bomKey, dep );

        Map<DependencyManagementKey, String> bomMap = bomDepMap.get( srcBom );
        if ( bomMap == null )
        {
            bomMap = new HashMap<DependencyManagementKey, String>();
            bomDepMap.put( srcBom, bomMap );
        }

        bomMap.put( bomKey, version );
    }

    void addBOM( final File bom, final MavenProject project )
        throws VManException
    {
        final FullProjectKey key =
            new FullProjectKey( project.getGroupId(), project.getArtifactId(), project.getVersion() );
        if ( bomProjects.containsKey( key ) )
        {
            return;
        }

        bomProjects.put( key, project );

        startBomMap( bom, project.getGroupId(), project.getArtifactId(), project.getVersion() );

        if ( project.getDependencyManagement() != null && project.getDependencyManagement()
                                                                 .getDependencies() != null )
        {
            for ( final Dependency dep : project.getDependencyManagement()
                                                .getDependencies() )
            {
                mapDependency( bom, dep );
            }
        }

        final Properties properties = project.getProperties();
        if ( properties != null )
        {
            final String relocations = properties.getProperty( RELOCATIONS_KEY );
            logger.info( "Got relocations:\n\n" + relocations );
            if ( relocations != null )
            {
                logger.warn( "[DEPRECATED] BOM-based coordinate relocations have been replaced by the "
                    + Cli.RELOCATIONS_PROPERTY
                    + " configuration, which specifies a URL to a properties file. Please use this instead." );

                relocatedCoords.addBomRelocations( bom, parseProperties( relocations ) );
            }

            final String mappings = properties.getProperty( MAPPINGS_KEY );
            logger.info( "Got mappings:\n\n" + mappings );
            if ( mappings != null )
            {
                logger.warn( "[DEPRECATED] BOM-based property mappings have been replaced by the "
                    + Cli.PROPERTY_MAPPINGS_PROPERTY
                    + " configuration, which specifies a URL to a properties file. Please use this instead." );

                propertyMappings.addBomPropertyMappings( bom, parseProperties( mappings ) );
            }
        }

        logger.info( "Updating property mappings from " + project.getId() );

        // NOTE: parent properties are inherited into the BOM by the time the MavenProject instance
        // is created, so we don't need to traverse up to the parent; we should have everything here.
        propertyMappings.updateProjectMap( project.getProperties() );
    }

    FullProjectKey getRelocation( final ProjectKey key )
    {
        return relocatedCoords.getRelocation( key );
    }

    List<FullProjectKey> getBomCoords()
    {
        return new ArrayList<FullProjectKey>( bomProjects.keySet() );
    }

    CoordinateRelocations getRelocations()
    {
        return relocatedCoords;
    }

    void setToolchain( final File toolchainFile, final MavenProject project )
    {
        toolchainKey = new FullProjectKey( project );
        this.toolchainProject = project;

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

    Set<VersionlessProjectKey> getRemovedPlugins()
    {
        return removedPlugins;
    }

    List<WildcardProjectKey> getRemovedTests()
    {
        return removedTests;
    }

    Set<VersionlessProjectKey> getExtensionsWhitelist()
    {
        return extensionsWhitelist;
    }

    List<String> getModderKeys()
    {
        return modderKeys;
    }

    FullProjectKey getToolchainKey()
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
        return bomProjects.containsKey( key );
    }

    boolean isToolchainReference( final Parent parent )
    {
        return toolchainKey == null ? false
                        : new VersionlessProjectKey( toolchainKey ).equals( new VersionlessProjectKey( parent ) );
    }

    synchronized void setCurrentProjects( final Set<Project> projects )
    {
        currentProjects.clear();
        currentProjectsByKey.clear();
        for ( final Project project : projects )
        {
            currentProjects.add( project );
            currentProjectsByKey.put( toVersionlessKey( project.getKey() ), project );
        }
    }

    synchronized void setPeekedPoms( final Map<FullProjectKey, File> peekedPoms )
    {
        if ( peekedPoms == null || peekedPoms.isEmpty() )
        {
            return;
        }

        this.peekedPoms.clear();
        for ( final Map.Entry<FullProjectKey, File> entry : peekedPoms.entrySet() )
        {
            //            if ( excludedModulePoms != null
            //                && excludedModulePoms.contains( new VersionlessProjectKey( entry.getKey() ) ) )
            //            {
            //                continue;
            //            }

            this.peekedPoms.put( entry.getKey(), entry.getValue() );
            this.peekedPomsReverse.put( entry.getValue(), entry.getKey() );
        }
    }

    boolean isExcludedModulePom( final File pom )
    {
        final FullProjectKey key = peekedPomsReverse.get( pom );
        if ( key == null )
        {
            return false;
        }

        final VersionlessProjectKey vpk = new VersionlessProjectKey( key );
        return excludedModulePoms.contains( vpk );
    }

    LinkedHashSet<Project> getCurrentProjects()
    {
        return currentProjects;
    }

    boolean isCurrentProject( final ProjectKey key )
    {
        final VersionlessProjectKey vk = toVersionlessKey( key );
        return currentProjectsByKey.containsKey( vk );
    }

    PropertyMappings getPropertyMapping()
    {
        return propertyMappings;
    }

    Project getCurrentProject( final ProjectKey key )
    {
        return currentProjectsByKey.get( toVersionlessKey( key ) );
    }

    File getPeekedPom( final FullProjectKey key )
    {
        logger.info( "STORE: Peeked POM for: '%s' is: %s", key, peekedPoms.get( key ) );
        return peekedPoms.get( key );
    }

    private VersionlessProjectKey toVersionlessKey( final ProjectKey key )
    {
        return (VersionlessProjectKey) ( key instanceof FullProjectKey ? new VersionlessProjectKey( key ) : key );
    }

    MavenProject getToolchainProject()
    {
        return toolchainProject;
    }

    MavenProject getBOMProject( final FullProjectKey key )
    {
        return bomProjects.get( key );
    }

    Map<FullProjectKey, File> getPeekedPoms()
    {
        return peekedPoms;
    }

    void addPeekPom( final FullProjectKey key, final File pom )
    {
        peekedPoms.put( key, pom );
    }

    Set<VersionlessProjectKey> getExcludedModulePoms()
    {
        return excludedModulePoms;
    }

}
