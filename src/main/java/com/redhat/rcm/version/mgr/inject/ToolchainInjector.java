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

package com.redhat.rcm.version.mgr.inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.merge.MavenModelMerger;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.mgr.VersionManagerSession;
import com.redhat.rcm.version.model.FullProjectKey;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.model.VersionlessProjectKey;

@Component( role = PomInjector.class, hint = "toolchain-realignment" )
public class ToolchainInjector
    implements PomInjector
{

    private static final Logger LOGGER = Logger.getLogger( ToolchainInjector.class );

    private final InjectionMerger merger = new InjectionMerger();

    @Override
    public boolean injectChanges( final Project project, final VersionManagerSession session )
    {
        boolean changed = false;

        changed = attemptToolchainParentInjection( project, session ) || changed;

        final Set<VersionlessProjectKey> pluginRefs = new HashSet<VersionlessProjectKey>();
        pluginRefs.addAll( session.getPluginReferences( new VersionlessProjectKey( project ) ) );
        accumulatePluginRefs( project, pluginRefs, session );

        changed = stripToolchainPluginInfo( project, session ) || changed;
        changed = stripRemovedPlugins( project, session ) || changed;

        if ( !session.hasParentInGraph( project ) )
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
        changed = adjustReportPlugins( project, session ) || changed;

        return changed;
    }

    private boolean adjustReportPlugins( final Project project, final VersionManagerSession session )
    {
        boolean changed = false;

        List<ReportPlugin> reportPlugins = project.getReportPlugins();
        if ( reportPlugins != null )
        {
            for ( ReportPlugin plugin : reportPlugins )
            {
                final VersionlessProjectKey pluginKey = new VersionlessProjectKey( plugin );
                final Plugin managedPlugin = session.getManagedPlugin( pluginKey );
                if ( managedPlugin != null && !managedPlugin.getVersion().equals( plugin.getVersion() ) )
                {
                    plugin.setVersion( managedPlugin.getVersion() );
                    changed = true;
                }
            }
        }

        return changed;
    }

    private boolean attemptToolchainParentInjection( final Project project, final VersionManagerSession session )
    {
        final Model model = project.getModel();
        final FullProjectKey toolchainKey = session.getToolchainKey();

        boolean changed = false;
        Parent parent = model.getParent();
        if ( toolchainKey != null )
        {
            if ( parent == null )
            {
                LOGGER.info( "Injecting toolchain as parent for: " + project.getKey() );

                parent = new Parent();
                parent.setGroupId( toolchainKey.getGroupId() );
                parent.setArtifactId( toolchainKey.getArtifactId() );
                parent.setVersion( toolchainKey.getVersion() );

                model.setParent( parent );
                session.connectProject( project );

                changed = true;
            }
            else
            {
                VersionlessProjectKey vtk =
                    new VersionlessProjectKey( toolchainKey.getGroupId(), toolchainKey.getArtifactId() );

                VersionlessProjectKey vpk = new VersionlessProjectKey( parent );

                if ( vtk.equals( vpk ) )
                {
                    parent.setVersion( toolchainKey.getVersion() );
                }
            }
        }

        return changed;
    }

    private boolean injectPluginManagement( final Project project, final Set<VersionlessProjectKey> pluginRefs,
                                            final VersionManagerSession session )
    {
        LOGGER.info( "Injecting pluginManagement section from toolchain for: " + project.getKey() );

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
                LOGGER.info( "Adding plugin: " + pluginRef );

                pm.addPlugin( managed );
            }
            else
            {
                LOGGER.info( "Merging plugin: " + pluginRef );

                merger.mergePlugin( managed, existing );
            }

            changed = true;
        }

        return changed;
    }

    private boolean injectPluginUsages( final Project project, final Set<VersionlessProjectKey> pluginRefs,
                                        final VersionManagerSession session )
    {
        LOGGER.info( "Injecting plugin usages from toolchain for project: " + project.getKey() );

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
                LOGGER.info( "Adding plugin: " + key );

                build.addPlugin( injected );
                pluginRefs.add( key );
            }
            else
            {
                LOGGER.info( "Merging plugin: " + key );

                merger.mergePlugin( injected, existing );
            }

            changed = true;
        }

        return changed;
    }

    private boolean stripRemovedPlugins( final Project project, final VersionManagerSession session )
    {
        LOGGER.info( "Deleting plugins marked for removal for project: " + project.getKey() );

        boolean changed = false;
        final Set<VersionlessProjectKey> removedPlugins = session.getRemovedPlugins();

        if ( removedPlugins.isEmpty() )
        {
            return changed;
        }

        final Model original = project.getModel();
        Build build = original.getBuild();

        if ( build != null )
        {
            Map<String, Plugin> pluginMap = new HashMap<String, Plugin>( build.getPluginsAsMap() );

            for ( final VersionlessProjectKey key : removedPlugins )
            {
                final Plugin existing = pluginMap.get( key.getId() );

                if ( existing != null )
                {
                    LOGGER.info( "Removing plugin: " + key );
                    build.removePlugin( existing );

                    changed = true;
                }
            }

            PluginManagement pm = build.getPluginManagement();
            if ( pm != null )
            {
                pluginMap = pm.getPluginsAsMap();

                for ( final VersionlessProjectKey key : removedPlugins )
                {
                    final Plugin existing = pluginMap.get( key.getId() );

                    if ( existing != null )
                    {
                        LOGGER.info( "Removing managed plugin: " + key );
                        pm.removePlugin( existing );

                        changed = true;
                    }
                }

            }
        }

        Reporting reporting = original.getReporting();
        if ( reporting != null )
        {
            Map<String, ReportPlugin> pluginMap = new HashMap<String, ReportPlugin>( reporting.getReportPluginsAsMap() );
            for ( VersionlessProjectKey key : removedPlugins )
            {
                ReportPlugin existing = pluginMap.get( key.getId() );
                if ( existing != null )
                {
                    LOGGER.info( "Removing report plugin: " + key );
                    reporting.removePlugin( existing );

                    changed = true;
                }
            }
        }

        return changed;
    }

    private void accumulatePluginRefs( final Project project, final Set<VersionlessProjectKey> pluginRefs,
                                       final VersionManagerSession session )
    {
        final VersionlessProjectKey parentKey =
            session.hasParentInGraph( project ) ? new VersionlessProjectKey( project.getParent() ) : null;

        List<Plugin> plugins = project.getPlugins();
        if ( plugins != null )
        {
            for ( final Plugin plugin : plugins )
            {
                final VersionlessProjectKey pluginKey = new VersionlessProjectKey( plugin );
                final Plugin managedPlugin = session.getManagedPlugin( pluginKey );
                if ( managedPlugin != null )
                {
                    if ( parentKey != null )
                    {
                        session.addPluginReference( parentKey, pluginKey );
                    }
                    else
                    {
                        pluginRefs.add( pluginKey );
                    }
                }
                else
                {
                    session.addUnmanagedPlugin( project.getPom(), pluginKey );
                }
            }
        }

        plugins = project.getManagedPlugins();
        if ( plugins != null )
        {
            for ( final Plugin plugin : plugins )
            {
                final VersionlessProjectKey pluginKey = new VersionlessProjectKey( plugin );
                final Plugin managedPlugin = session.getManagedPlugin( pluginKey );
                if ( managedPlugin != null )
                {
                    if ( parentKey != null )
                    {
                        session.addPluginReference( parentKey, pluginKey );
                    }
                    else
                    {
                        pluginRefs.add( pluginKey );
                    }
                }
                else
                {
                    session.addUnmanagedPlugin( project.getPom(), pluginKey );
                }
            }
        }

        List<ReportPlugin> reportPlugins = project.getReportPlugins();
        if ( reportPlugins != null )
        {
            for ( ReportPlugin plugin : reportPlugins )
            {
                final VersionlessProjectKey pluginKey = new VersionlessProjectKey( plugin );
                final Plugin managedPlugin = session.getManagedPlugin( pluginKey );
                if ( managedPlugin != null )
                {
                    if ( parentKey != null )
                    {
                        session.addPluginReference( parentKey, pluginKey );
                    }
                    else
                    {
                        pluginRefs.add( pluginKey );
                    }
                }
                else
                {
                    session.addUnmanagedPlugin( project.getPom(), pluginKey );
                }
            }
        }
    }

    private boolean stripToolchainPluginInfo( final Project project, final VersionManagerSession session )
    {
        LOGGER.info( "Stripping toolchain plugin info for project: " + project.getKey() );

        boolean changed = stripToolchainPluginInfo( project, project.getPlugins(), session );

        LOGGER.info( "Stripping toolchain pluginManagement info for project: " + project.getKey() );

        changed = stripToolchainPluginInfo( project, project.getManagedPlugins(), session ) || changed;

        return changed;
    }

    private boolean stripToolchainPluginInfo( final Project project, final List<Plugin> plugins,
                                              final VersionManagerSession session )
    {
        final Model originalModel = project.getModel();
        final Build build = originalModel.getBuild();

        List<Plugin> originalPlugins = null;
        if ( build != null )
        {
            originalPlugins = build.getPlugins();
        }

        if ( originalPlugins == null )
        {
            originalPlugins = Collections.emptyList();
        }

        boolean changed = false;
        if ( plugins != null )
        {
            for ( final Plugin plugin : new ArrayList<Plugin>( plugins ) )
            {
                final VersionlessProjectKey pluginKey = new VersionlessProjectKey( plugin );
                final Plugin managedPlugin = session.getManagedPlugin( pluginKey );

                if ( managedPlugin != null )
                {
                    LOGGER.info( "Stripping plugin version from: " + pluginKey );

                    plugin.setVersion( null );

                    if ( isEmpty( plugin.getDependencies() ) && isEmpty( plugin.getExecutions() )
                        && plugin.getConfiguration() == null )
                    {
                        LOGGER.info( "Removing plugin: " + pluginKey );

                        plugins.remove( plugin );
                    }

                    changed = true;
                }
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
