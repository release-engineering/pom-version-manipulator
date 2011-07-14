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

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.merge.MavenModelMerger;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.mgr.VersionManagerSession;
import com.redhat.rcm.version.model.VersionlessProjectKey;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component( role = PomInjector.class, hint = "toolchain-realignment" )
public class ToolchainInjector
    implements PomInjector
{

    private final InjectionMerger merger = new InjectionMerger();

    public boolean injectChanges( MavenProject project, VersionManagerSession session )
    {
        boolean changed = false;

        Set<VersionlessProjectKey> pluginRefs = new HashSet<VersionlessProjectKey>();
        pluginRefs.addAll( session.getPluginReferences( new VersionlessProjectKey( project ) ) );
        pluginRefs.addAll( accumulatePluginRefs( project, session ) );

        if ( !session.hasParentInGraph( project ) )
        {
            changed = changed || injectPluginManagement( project, pluginRefs, session );
            changed = changed || injectPluginUsages( project, session );
        }

        return changed;
    }

    private boolean injectPluginManagement( MavenProject project, Set<VersionlessProjectKey> pluginRefs,
                                            VersionManagerSession session )
    {
        boolean changed = false;
        if ( pluginRefs.isEmpty() )
        {
            return changed;
        }

        Model original = project.getOriginalModel();
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

        Map<String, Plugin> pluginMap = pm.getPluginsAsMap();
        for ( VersionlessProjectKey pluginRef : pluginRefs )
        {
            Plugin managed = session.getManagedPlugin( pluginRef );
            Plugin existing = pluginMap.get( pluginRef.getId() );
            if ( existing == null )
            {
                pm.addPlugin( managed );
            }
            else
            {
                merger.mergePlugin( managed, existing );
            }

            changed = true;
        }

        return changed;
    }

    private boolean injectPluginUsages( MavenProject project, VersionManagerSession session )
    {
        boolean changed = false;
        Map<VersionlessProjectKey, Plugin> injectedPlugins = session.getInjectedPlugins();

        if ( injectedPlugins.isEmpty() )
        {
            return changed;
        }

        Model original = project.getOriginalModel();
        Build build = original.getBuild();
        if ( build == null )
        {
            build = new Build();
            original.setBuild( build );
            changed = true;
        }

        Map<String, Plugin> pluginMap = build.getPluginsAsMap();

        for ( Map.Entry<VersionlessProjectKey, Plugin> entry : injectedPlugins.entrySet() )
        {
            VersionlessProjectKey key = entry.getKey();

            Plugin injected = entry.getValue();
            Plugin existing = pluginMap.get( key.getId() );

            if ( existing == null )
            {
                build.addPlugin( injected );
            }
            else
            {
                merger.mergePlugin( injected, existing );
            }

            changed = true;
        }

        return changed;
    }

    private Set<VersionlessProjectKey> accumulatePluginRefs( MavenProject project, VersionManagerSession session )
    {
        VersionlessProjectKey projectKey = new VersionlessProjectKey( project );
        
        List<Plugin> plugins = project.getBuildPlugins();

        Model originalModel = project.getOriginalModel();
        Build build = originalModel.getBuild();

        List<Plugin> originalPlugins = null;
        if ( build != null )
        {
            originalPlugins = build.getPlugins();
        }

        if ( originalPlugins == null )
        {
            originalPlugins = Collections.emptyList();
        }

        Set<VersionlessProjectKey> pluginKeys = new HashSet<VersionlessProjectKey>();

        if ( plugins != null )
        {
            for ( Plugin plugin : plugins )
            {
                VersionlessProjectKey pluginKey = new VersionlessProjectKey( plugin );
                Plugin managedPlugin = session.getManagedPlugin( pluginKey );

                int originalIdx = originalPlugins.indexOf( plugin );
                Plugin originalPlugin = originalIdx > -1 ? originalPlugins.get( originalIdx ) : null;

                if ( managedPlugin != null )
                {
                    if ( originalPlugin != null )
                    {
                        originalPlugin.setVersion( null );
                    }

                    if ( projectKey != null )
                    {
                        session.addPluginReference( projectKey, pluginKey );
                    }
                    else
                    {
                        pluginKeys.add( pluginKey );
                    }
                }
                else
                {
                    session.addUnmanagedPlugin( project.getFile(), pluginKey );
                }
            }
        }

        return pluginKeys;
    }

    private static final class InjectionMerger
        extends MavenModelMerger
    {
        public void mergePlugin( Plugin injected, Plugin existing )
        {
            super.mergePlugin( injected, existing, true, Collections.emptyMap() );
        }
    }

}
