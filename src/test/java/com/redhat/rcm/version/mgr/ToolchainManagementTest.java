/*
 *  Copyright (C) 2011 John Casey.
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.redhat.rcm.version.mgr;

import static org.apache.commons.io.FileUtils.copyFile;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.junit.BeforeClass;
import org.junit.Test;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.inject.ToolchainInjector;
import com.redhat.rcm.version.model.FullProjectKey;

public class ToolchainManagementTest
    extends AbstractVersionManagerTest
{

    private static final String TOOLCHAIN_TEST_POMS = "toolchain/";

    private static final String TOOLCHAIN_PATH = TOOLCHAIN_TEST_POMS + "toolchain-1.0.pom";

    private static FullProjectKey toolchainKey;

    @BeforeClass
    public static void setupLogging()
    {
        Map<Class<?>, Level> levels = new HashMap<Class<?>, Level>();
        levels.put( ToolchainInjector.class, Level.INFO );

        setupLogging( levels );
    }

    @Test
    public void adjustNonManagedPluginForInheritedToolchain()
        throws Throwable
    {
        Model changed =
            adjustSingle( "Adjust single non-managed plugin in POM inherited from toolchain",
                          "toolchain-child-nonManaged-1.0.pom" );
        Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        List<Plugin> plugins = build.getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );

        Plugin plugin = plugins.get( 0 );
        assertThat( plugin.getArtifactId(), equalTo( "maven-compiler-plugin" ) );
        assertThat( plugin.getVersion(), nullValue() );
    }

    @Test
    public void adjustManagedPluginForInheritedToolchain()
        throws Throwable
    {
        Model changed =
            adjustSingle( "Adjust managed plugin in POM inherited from toolchain", "toolchain-child-managed-1.0.pom" );

        Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        PluginManagement pm = build.getPluginManagement();
        assertThat( pm, notNullValue() );

        List<Plugin> plugins = pm.getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );

        Plugin plugin = plugins.get( 0 );
        assertThat( plugin.getArtifactId(), equalTo( "maven-compiler-plugin" ) );
        assertThat( plugin.getVersion(), nullValue() );
    }

    @Test
    public void adjustNonManagedPluginForNonInheritedToolchain()
        throws Throwable
    {
        Model changed =
            adjustSingle( "Adjust non-managed plugin in POM NOT inheriting from toolchain",
                          "toolchain-externalParent-nonManaged-1.0.pom" );

        Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        List<Plugin> plugins = build.getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );

        Plugin plugin = plugins.get( 0 );
        assertThat( plugin.getArtifactId(), equalTo( "maven-compiler-plugin" ) );
        assertThat( plugin.getVersion(), nullValue() );

        PluginManagement pm = build.getPluginManagement();
        assertThat( pm, notNullValue() );

        Map<String, Plugin> pluginMap = pm.getPluginsAsMap();
        assertThat( pluginMap, notNullValue() );

        plugin = pluginMap.get( "org.apache.maven.plugins:maven-compiler-plugin" );
        assertThat( plugin.getVersion(), equalTo( "2.0" ) );
    }

    @Test
    public void adjustNonManagedPluginForInjectedToolchainParent()
        throws Throwable
    {
        Model changed =
            adjustSingle( "Adjust single non-managed plugin in POM to be inherited from toolchain",
                          "toolchain-noParent-nonManaged-1.0.pom" );

        Parent parent = changed.getParent();
        assertThat( parent, notNullValue() );

        FullProjectKey tk = getToolchainKey();
        assertThat( parent.getGroupId(), equalTo( tk.getGroupId() ) );
        assertThat( parent.getArtifactId(), equalTo( tk.getArtifactId() ) );
        assertThat( parent.getVersion(), equalTo( tk.getVersion() ) );

        Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        List<Plugin> plugins = build.getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );

        Plugin plugin = plugins.get( 0 );
        assertThat( plugin.getArtifactId(), equalTo( "maven-compiler-plugin" ) );
        assertThat( plugin.getVersion(), nullValue() );
    }

    @Test
    public void removeEmptyNonManagedPluginForInheritedToolchain()
        throws Throwable
    {
        Model changed =
            adjustSingle( "Adjust single non-managed plugin in POM inherited from toolchain",
                          "toolchain-child-nonManaged-emptyPlugin-1.0.pom" );
        Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        List<Plugin> plugins = build.getPlugins();
        assertThat( plugins == null || plugins.isEmpty(), is( true ) );
    }

    @Test
    public void removeEmptyNonManagedPluginForInjectedToolchainParent()
        throws Throwable
    {
        Model changed =
            adjustSingle( "Adjust single non-managed plugin in POM inherited from toolchain",
                          "toolchain-noParent-nonManaged-emptyPlugin-1.0.pom" );

        Parent parent = changed.getParent();
        assertThat( parent, notNullValue() );

        FullProjectKey tk = getToolchainKey();
        assertThat( parent.getGroupId(), equalTo( tk.getGroupId() ) );
        assertThat( parent.getArtifactId(), equalTo( tk.getArtifactId() ) );
        assertThat( parent.getVersion(), equalTo( tk.getVersion() ) );

        Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        List<Plugin> plugins = build.getPlugins();
        assertThat( plugins == null || plugins.isEmpty(), is( true ) );
    }

    @Test
    public void removeEmptyManagedPluginForInheritedToolchain()
        throws Throwable
    {
        Model changed =
            adjustSingle( "Adjust managed plugin in POM inherited from toolchain",
                          "toolchain-child-managed-emptyPlugin-1.0.pom" );

        Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        PluginManagement pm = build.getPluginManagement();
        assertThat( pm, notNullValue() );

        List<Plugin> plugins = pm.getPlugins();
        assertThat( plugins == null || plugins.isEmpty(), is( true ) );
    }

    @Test
    public void removeEmptyNonManagedPluginForNonInheritedToolchain()
        throws Throwable
    {
        Model changed =
            adjustSingle( "Adjust non-managed plugin in POM NOT inheriting from toolchain",
                          "toolchain-externalParent-nonManaged-emptyPlugin-1.0.pom" );

        Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        List<Plugin> plugins = build.getPlugins();
        assertThat( plugins == null || plugins.isEmpty(), is( true ) );

        PluginManagement pm = build.getPluginManagement();
        assertThat( pm, notNullValue() );

        Map<String, Plugin> pluginMap = pm.getPluginsAsMap();
        assertThat( pluginMap, notNullValue() );

        Plugin plugin = pluginMap.get( "org.apache.maven.plugins:maven-compiler-plugin" );
        assertThat( plugin.getVersion(), equalTo( "2.0" ) );
    }

    private Model adjustSingle( String description, String pomPath )
        throws Throwable
    {
        System.setProperty( "debug", Boolean.toString( true ) );
        System.out.println( "TESTING: " + description );

        final File srcPom = getResourceFile( TOOLCHAIN_TEST_POMS + pomPath );
        final String toolchain = getResourceFile( TOOLCHAIN_PATH ).getAbsolutePath();

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        final VersionManagerSession session = new VersionManagerSession( workspace, reports, false, false );

        final Set<File> modified = vman.modifyVersions( pom, null, toolchain, session );
        assertNoErrors( session );

        Set<Model> changedModels = loadModels( modified );
        assertThat( changedModels.size(), equalTo( 1 ) );

        return changedModels.iterator().next();
    }

    private static FullProjectKey getToolchainKey()
        throws IOException, VManException
    {
        if ( toolchainKey == null )
        {
            toolchainKey = loadProjectKey( TOOLCHAIN_PATH );
        }

        return toolchainKey;
    }

}
