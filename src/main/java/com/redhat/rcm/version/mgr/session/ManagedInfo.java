package com.redhat.rcm.version.mgr.session;

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

    private final Set<FullProjectKey> bomCoords = new LinkedHashSet<FullProjectKey>();

    private final Relocations relocations = new Relocations();

    private final Map<VersionlessProjectKey, String> depMap = new HashMap<VersionlessProjectKey, String>();

    private final Map<File, Map<VersionlessProjectKey, String>> bomDepMap =
        new HashMap<File, Map<VersionlessProjectKey, String>>();

    private final Map<VersionlessProjectKey, Plugin> managedPlugins =
        new LinkedHashMap<VersionlessProjectKey, Plugin>();

    private final Map<VersionlessProjectKey, Plugin> injectedPlugins =
        new LinkedHashMap<VersionlessProjectKey, Plugin>();

    private FullProjectKey toolchainKey;

    private final Set<VersionlessProjectKey> removedPlugins = new HashSet<VersionlessProjectKey>();

    private final VersionManagerSession session;

    private final Set<Project> currentProjects = new LinkedHashSet<Project>();

    private final Set<VersionlessProjectKey> currentProjectKeys = new LinkedHashSet<VersionlessProjectKey>();

    ManagedInfo( final VersionManagerSession session )
    {
        this.session = session;
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

    void addBOM( final File bom, final MavenProject project )
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
    }

    private void addRelocations( final File bom, final String relocationsStr )
    {
        relocations.addBomRelocations( bom, relocationsStr, session );
    }

    FullProjectKey getRelocation( final ProjectKey key )
    {
        return relocations.getRelocation( key );
    }

    Set<FullProjectKey> getBomCoords()
    {
        return bomCoords;
    }

    Relocations getRelocations()
    {
        return relocations;
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
        for ( final String rm : removedPlugins )
        {
            this.removedPlugins.add( new VersionlessProjectKey( rm ) );
        }
    }

    Set<VersionlessProjectKey> getRemovedPlugins()
    {
        return removedPlugins;
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

}
