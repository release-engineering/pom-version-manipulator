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

package com.redhat.rcm.version.mgr.mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Model;
import org.apache.maven.model.ModelBase;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.merge.MavenModelMerger;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectModder.class, hint = "toolchain-realignment" )
public class ToolchainModder
    implements ProjectModder
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final InjectionMerger merger = new InjectionMerger();

    @Override
    public String getDescription()
    {
        return "Forcibly realign POM build section to use plugins declared in the supplied toolchain POM (if present).";
    }

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        boolean changed = false;

        if ( session.getToolchainKey() == null )
        {
            return changed;
        }

        final Set<VersionlessProjectKey> pluginRefs = new HashSet<VersionlessProjectKey>();
        pluginRefs.addAll( session.getChildPluginReferences( new VersionlessProjectKey( project.getKey() ) ) );

        final List<ModelBase> bases = new ArrayList<ModelBase>();
        bases.add( project.getModel() );
        final List<Profile> profiles = project.getModel()
                                              .getProfiles();
        if ( profiles != null && !profiles.isEmpty() )
        {
            bases.addAll( profiles );

        }

        changed = stripRemovedPlugins( project, bases, session ) || changed;
        changed = stripToolchainPluginInfo( project, bases, pluginRefs, session ) || changed;

        if ( project.getParent() == null )
        {
            changed = injectPluginUsages( project, pluginRefs, session ) || changed;
            changed = injectPluginManagement( project, pluginRefs, session ) || changed;
        }

        // NOTE: Having versions in pluginManagement isn't normally enough for
        // report plugins, unless they're also during the normal build process.
        //
        // So, we have to inject the versions directly into the reporting section.
        //
        // This happens regardless of whether the toolchain is in the ancestry of the POM or not.
        changed = adjustReportPlugins( project, bases, pluginRefs, session ) || changed;

        return changed;
    }

    private boolean adjustReportPlugins( final Project project, final List<ModelBase> bases, final Set<VersionlessProjectKey> pluginRefs,
                                         final VersionManagerSession session )
    {
        final VersionlessProjectKey parentKey = project.getParent() != null ? new VersionlessProjectKey( project.getParent() ) : null;

        boolean changed = false;

        for ( final ModelBase base : bases )
        {
            final List<ReportPlugin> reportPlugins = project.getReportPlugins( base );
            if ( reportPlugins != null )
            {
                int idx = 0;
                for ( ReportPlugin plugin : reportPlugins )
                {
                    VersionlessProjectKey pluginKey = new VersionlessProjectKey( plugin );
                    final FullProjectKey relocation = session.getRelocation( pluginKey );
                    if ( relocation != null )
                    {
                        session.addRelocatedCoordinate( project.getPom(), pluginKey, relocation );

                        changed = true;

                        final ReportPlugin plug = new ReportPlugin();
                        plug.setGroupId( relocation.getGroupId() );
                        plug.setArtifactId( relocation.getArtifactId() );

                        if ( session.isStrict() )
                        {
                            plug.setVersion( relocation.getVersion() );
                        }

                        plug.setConfiguration( plugin.getConfiguration() );
                        plug.setReportSets( plugin.getReportSets() );
                        plug.setInherited( plugin.isInherited() );
                        plug.setInherited( plugin.getInherited() );
                        plug.setLocation( "", plugin.getLocation( "" ) );

                        if ( plugin.getLocation( "inherited" ) != null )
                        {
                            plug.setLocation( "inherited", plugin.getLocation( "inherited" ) );
                        }

                        reportPlugins.set( idx, plug );

                        pluginKey = new VersionlessProjectKey( relocation );
                        plugin = plug;
                    }

                    final Plugin managedPlugin = session.getManagedPlugin( pluginKey );
                    if ( managedPlugin != null && !managedPlugin.getVersion()
                                                                .equals( plugin.getVersion() ) )
                    {
                        plugin.setVersion( managedPlugin.getVersion() );
                        changed = true;

                        if ( parentKey != null )
                        {
                            session.addChildPluginReference( parentKey, pluginKey );
                        }
                        else
                        {
                            pluginRefs.add( pluginKey );
                        }
                    }
                    else
                    {
                        session.addUnmanagedPlugin( project.getPom(), plugin );
                    }

                    idx++;
                }
            }
        }

        return changed;
    }

    private boolean injectPluginManagement( final Project project, final Set<VersionlessProjectKey> pluginRefs, final VersionManagerSession session )
    {
        logger.info( "Injecting pluginManagement section from toolchain for: " + project.getKey() );

        boolean changed = false;
        if ( pluginRefs.isEmpty() )
        {
            return changed;
        }

        final Model original = project.getModel();
        Build build = original.getBuild();
        if ( build == null )
        {
            build = new Build();
            original.setBuild( build );
            changed = true;
        }

        PluginManagement pm = build.getPluginManagement();
        if ( pm == null )
        {
            pm = new PluginManagement();
            build.setPluginManagement( pm );
            changed = true;
        }

        final Map<String, Plugin> pluginMap = pm.getPluginsAsMap();
        for ( final VersionlessProjectKey pluginRef : pluginRefs )
        {
            final Plugin managed = session.getManagedPlugin( pluginRef );
            final Plugin existing = pluginMap.get( pluginRef.getId() );
            if ( existing == null )
            {
                logger.info( "Adding plugin: " + pluginRef );

                pm.addPlugin( managed );
            }
            else
            {
                logger.info( "Merging plugin: " + pluginRef );

                merger.mergePlugin( managed, existing );
            }

            changed = true;
        }

        return changed;
    }

    private boolean injectPluginUsages( final Project project, final Set<VersionlessProjectKey> pluginRefs, final VersionManagerSession session )
    {
        logger.info( "Injecting plugin usages from toolchain for project: " + project.getKey() );

        boolean changed = false;
        final Map<VersionlessProjectKey, Plugin> injectedPlugins = session.getInjectedPlugins();

        if ( injectedPlugins.isEmpty() )
        {
            return changed;
        }

        final Model original = project.getModel();
        Build build = original.getBuild();
        if ( build == null )
        {
            build = new Build();
            original.setBuild( build );
        }

        final Map<String, Plugin> pluginMap = build.getPluginsAsMap();

        for ( final Map.Entry<VersionlessProjectKey, Plugin> entry : injectedPlugins.entrySet() )
        {
            final VersionlessProjectKey key = entry.getKey();

            final Plugin injected = entry.getValue();
            final Plugin existing = pluginMap.get( key.getId() );

            if ( existing == null )
            {
                logger.info( "Adding plugin: " + key );

                build.addPlugin( injected );
                pluginRefs.add( key );
            }
            else
            {
                logger.info( "Merging plugin: " + key );

                merger.mergePlugin( injected, existing );
            }

            changed = true;
        }

        return changed;
    }

    private boolean stripRemovedPlugins( final Project project, final List<ModelBase> bases, final VersionManagerSession session )
    {
        logger.info( "Deleting plugins marked for removal for project: " + project.getKey() );

        boolean changed = false;
        final Set<VersionlessProjectKey> removedPlugins = session.getRemovedPlugins();

        if ( removedPlugins.isEmpty() )
        {
            return changed;
        }

        for ( final ModelBase base : bases )
        {
            final BuildBase build = project.getBuild( base );

            if ( build != null )
            {
                Map<String, Plugin> pluginMap = new HashMap<String, Plugin>( build.getPluginsAsMap() );

                for ( final VersionlessProjectKey key : removedPlugins )
                {
                    final Plugin existing = pluginMap.get( key.getId() );

                    if ( existing != null )
                    {
                        logger.info( "Removing plugin: " + key );
                        build.removePlugin( existing );

                        changed = true;
                    }
                }

                final PluginManagement pm = build.getPluginManagement();
                if ( pm != null )
                {
                    pluginMap = pm.getPluginsAsMap();

                    for ( final VersionlessProjectKey key : removedPlugins )
                    {
                        final Plugin existing = pluginMap.get( key.getId() );

                        if ( existing != null )
                        {
                            logger.info( "Removing managed plugin: " + key );
                            pm.removePlugin( existing );

                            changed = true;
                        }
                    }

                }
            }

            final Reporting reporting = base.getReporting();
            if ( reporting != null )
            {
                final Map<String, ReportPlugin> pluginMap = new HashMap<String, ReportPlugin>( reporting.getReportPluginsAsMap() );
                for ( final VersionlessProjectKey key : removedPlugins )
                {
                    final ReportPlugin existing = pluginMap.get( key.getId() );
                    if ( existing != null )
                    {
                        logger.info( "Removing report plugin: " + key );
                        reporting.removePlugin( existing );

                        changed = true;
                    }
                }
            }
        }

        return changed;
    }

    private boolean stripToolchainPluginInfo( final Project project, final List<ModelBase> bases, final Set<VersionlessProjectKey> pluginRefs,
                                              final VersionManagerSession session )
    {
        logger.info( "Stripping toolchain plugin info for project: " + project.getKey() );
        boolean changed = false;
        for ( final ModelBase base : bases )
        {
            changed = stripToolchainPluginInfo( project, base, project.getPlugins( base ), pluginRefs, session, false ) || changed;

            logger.info( "Stripping toolchain pluginManagement info for project: " + project.getKey() );

            changed = stripToolchainPluginInfo( project, base, project.getManagedPlugins( base ), pluginRefs, session, true ) || changed;
        }

        project.flushPluginMaps();

        return changed;
    }

    private boolean stripToolchainPluginInfo( final Project project, final ModelBase base, final List<Plugin> plugins,
                                              final Set<VersionlessProjectKey> pluginRefs, final VersionManagerSession session, final boolean managed )
    {
        boolean changed = false;
        if ( plugins != null )
        {
            int idx = 0;
            for ( Plugin plugin : new ArrayList<Plugin>( plugins ) )
            {
                VersionlessProjectKey pluginKey = new VersionlessProjectKey( plugin );
                final FullProjectKey relocation = session.getRelocation( pluginKey );
                if ( relocation != null )
                {
                    session.addRelocatedCoordinate( project.getPom(), pluginKey, relocation );

                    changed = true;

                    final Plugin plug = new Plugin();
                    plug.setGroupId( relocation.getGroupId() );
                    plug.setArtifactId( relocation.getArtifactId() );

                    if ( session.isStrict() )
                    {
                        plug.setVersion( relocation.getVersion() );
                    }

                    plug.setConfiguration( plugin.getConfiguration() );
                    plug.setDependencies( plugin.getDependencies() );
                    plug.setExecutions( plugin.getExecutions() );
                    plug.setExtensions( plugin.isExtensions() );
                    plug.setExtensions( plugin.getExtensions() );
                    plug.setGoals( plugin.getGoals() );
                    plug.setInherited( plugin.isInherited() );
                    plug.setInherited( plugin.getInherited() );
                    plug.setLocation( "", plugin.getLocation( "" ) );

                    if ( plugin.getLocation( "inherited" ) != null )
                    {
                        plug.setLocation( "inherited", plugin.getLocation( "inherited" ) );
                    }

                    plugins.set( idx, plug );

                    pluginKey = new VersionlessProjectKey( relocation );
                    plugin = plug;
                }

                final Plugin managedPlugin = session.getManagedPlugin( pluginKey );

                final Plugin p = plugin.clone();

                if ( !session.isStrict() || managedPlugin != null )
                {
                    // Unless strict mode is set, remove the plugin version. It SHOULD come from the toolchain.
                    // The capture-POM will assist with adding missing plugins to the toolchain.
                    plugin.setVersion( null );
                    changed = true;
                }

                if ( managedPlugin != null )
                {
                    logger.info( "Stripping plugin version from: " + pluginKey );

                    if ( managed && isEmpty( plugin.getDependencies() ) && isEmpty( plugin.getExecutions() ) && plugin.getConfiguration() == null )
                    {
                        logger.info( "Removing plugin: " + pluginKey );

                        plugins.remove( plugin );
                        changed = true;
                    }

                    final VersionlessProjectKey parentKey = project.getVersionlessParentKey();
                    if ( parentKey != null )
                    {
                        session.addChildPluginReference( parentKey, pluginKey );
                    }
                    else
                    {
                        pluginRefs.add( pluginKey );
                    }
                }
                else
                {
                    session.addUnmanagedPlugin( project.getPom(), p );
                }

                idx++;
            }
        }

        return changed;
    }

    private boolean isEmpty( final Collection<?> collection )
    {
        return collection == null || collection.isEmpty();
    }

    private static final class InjectionMerger
        extends MavenModelMerger
    {
        public void mergePlugin( final Plugin injected, final Plugin existing )
        {
            super.mergePlugin( injected, existing, true, Collections.emptyMap() );
        }
    }

}
