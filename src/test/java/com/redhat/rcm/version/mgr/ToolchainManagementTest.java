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

package com.redhat.rcm.version.mgr;

import static com.redhat.rcm.version.testutil.PluginMatcher.mavenPlugin;
import static com.redhat.rcm.version.testutil.PluginMatcher.plugin;
import static com.redhat.rcm.version.testutil.TestProjectUtils.dumpModel;
import static com.redhat.rcm.version.testutil.TestProjectUtils.getResourceFile;
import static com.redhat.rcm.version.testutil.TestProjectUtils.loadModel;
import static com.redhat.rcm.version.testutil.TestProjectUtils.loadModels;
import static com.redhat.rcm.version.testutil.TestProjectUtils.loadProjectKey;
import static com.redhat.rcm.version.testutil.TestProjectUtils.newVersionManagerSession;
import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.lang.StringUtils.join;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginContainer;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.ReportSet;
import org.apache.maven.model.Reporting;
import org.apache.maven.project.MavenProject;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.redhat.rcm.version.fixture.LoggingFixture;
import com.redhat.rcm.version.mgr.mod.ToolchainModder;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.testutil.PluginMatcher;
import com.redhat.rcm.version.testutil.SessionBuilder;

public class ToolchainManagementTest
    extends AbstractVersionManagerTest
{

    private static final String TOOLCHAIN_TEST_POMS = "toolchain/";

    private static final String TOOLCHAIN_PATH = TOOLCHAIN_TEST_POMS + "toolchain-1.0.pom";

    private static final String EMPTY_TOOLCHAIN_PATH = TOOLCHAIN_TEST_POMS + "toolchain-empty-1.0.pom";

    private static final String REPORTS_TOOLCHAIN_PATH = TOOLCHAIN_TEST_POMS + "toolchain-reportPlugins-1.0.pom";

    private static final String ADDITIONS_TOOLCHAIN_PATH = TOOLCHAIN_TEST_POMS + "toolchain-addedPlugins-1.0.pom";

    private static FullProjectKey toolchainKey;

    @BeforeClass
    public static void enableLogging()
    {
        LoggingFixture.setupLogging();
    }

    @Before
    public void setup()
        throws Throwable
    {
        setupDirs();
        setupVersionManager();
    }

    @After
    public void teardown()
    {
        LoggingFixture.flushLogging();
    }

    @Test
    public void relocateParent()
        throws Throwable
    {
        final String path = "relocate-parent.pom";
        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );

        final String toolchainPath = EMPTY_TOOLCHAIN_PATH;
        final Model toolchainModel = loadModel( toolchainPath );
        final MavenProject toolchainProject = new MavenProject( toolchainModel );
        toolchainProject.setOriginalModel( toolchainModel );

        Parent parent = original.getParent();
        assertThat( parent, notNullValue() );
        assertThat( parent.getArtifactId(), equalTo( "old-parent" ) );
        assertThat( parent.getVersion(), equalTo( "1.0" ) );

        final Project project = new Project( original );
        final SessionBuilder builder =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "org.test:old-parent:1.0",
                                                                               "org.test:new-parent:2.0" );

        final VersionManagerSession session = builder.build();
        session.setToolchain( new File( toolchainPath ), toolchainProject );

        final boolean changed = new ToolchainModder().inject( project, session );

        dumpModel( project.getModel() );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );

        parent = project.getModel()
                        .getParent();
        assertThat( parent, notNullValue() );
        assertThat( parent.getArtifactId(), equalTo( "new-parent" ) );
        assertThat( parent.getVersion(), equalTo( "2.0" ) );
    }

    @Test
    public void relocatePlugin()
        throws Throwable
    {
        final String path = "relocate-plugin.pom";
        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );

        final String toolchainPath = EMPTY_TOOLCHAIN_PATH;
        final Model toolchainModel = loadModel( toolchainPath );
        final MavenProject toolchainProject = new MavenProject( toolchainModel );
        toolchainProject.setOriginalModel( toolchainModel );

        assertBuildPlugins( original, 1, plugin( "org.codehaus.mojo", "rat-maven-plugin" ) );
        assertPluginManagementPlugins( original, -1 );

        final Project project = new Project( original );
        final SessionBuilder builder =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "org.codehaus.mojo:rat-maven-plugin",
                                                                               "org.apache.rat:apache-rat-plugin:0.8" );

        final VersionManagerSession session = builder.build();
        session.setToolchain( new File( toolchainPath ), toolchainProject );

        final boolean changed = new ToolchainModder().inject( project, session );

        dumpModel( project.getModel() );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );

        assertBuildPlugins( project.getModel(), 1, plugin( "org.apache.rat", "apache-rat-plugin" ) );
        assertPluginManagementPlugins( project.getModel(), -1 );
    }

    @Test
    public void relocateManagedPlugin()
        throws Throwable
    {
        final String path = "relocate-managed-plugin.pom";
        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );

        final String toolchainPath = EMPTY_TOOLCHAIN_PATH;
        final Model toolchainModel = loadModel( toolchainPath );
        final MavenProject toolchainProject = new MavenProject( toolchainModel );
        toolchainProject.setOriginalModel( toolchainModel );

        assertBuildPlugins( original, -1 );
        assertPluginManagementPlugins( original, 1, plugin( "org.codehaus.mojo", "rat-maven-plugin" ) );

        final Project project = new Project( original );
        final SessionBuilder builder =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "org.codehaus.mojo:rat-maven-plugin",
                                                                               "org.apache.rat:apache-rat-plugin:0.8" );

        final VersionManagerSession session = builder.build();
        session.setToolchain( new File( toolchainPath ), toolchainProject );

        final boolean changed = new ToolchainModder().inject( project, session );

        dumpModel( project.getModel() );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );

        assertBuildPlugins( project.getModel(), -1 );
        assertPluginManagementPlugins( project.getModel(), 1, plugin( "org.apache.rat", "apache-rat-plugin" ) );
    }

    @Test
    public void relocateReportPlugin()
        throws Throwable
    {
        final String path = "relocate-report-plugin.pom";
        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );

        final String toolchainPath = EMPTY_TOOLCHAIN_PATH;
        final Model toolchainModel = loadModel( toolchainPath );
        final MavenProject toolchainProject = new MavenProject( toolchainModel );
        toolchainProject.setOriginalModel( toolchainModel );

        assertReportPlugins( original, 1, plugin( "org.codehaus.mojo", "rat-maven-plugin" ) );

        final Project project = new Project( original );
        final SessionBuilder builder =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "org.codehaus.mojo:rat-maven-plugin",
                                                                               "org.apache.rat:apache-rat-plugin:0.8" );

        final VersionManagerSession session = builder.build();
        session.setToolchain( new File( toolchainPath ), toolchainProject );

        final boolean changed = new ToolchainModder().inject( project, session );

        dumpModel( project.getModel() );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );

        assertReportPlugins( project.getModel(), 1, plugin( "org.apache.rat", "apache-rat-plugin" ) );
    }

    @Test
    public void adjustReportPluginWithoutVersion_InheritFromToolchain()
        throws Throwable
    {
        final String toolchainPath = REPORTS_TOOLCHAIN_PATH;
        final String path = "child-reportPlugin-noVersion-1.0.pom";

        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );

        assertReportPlugins( original, 1, mavenPlugin( "maven-checkstyle-plugin" ).version( null ) );
        assertPluginManagementPlugins( original, -1 );

        final Project project =
            adjustSingle( "Adjust report plugin version in POM that inherits from toolchain", path, toolchainPath );

        assertReportPlugins( project.getModel(), 1, mavenPlugin( "maven-checkstyle-plugin" ).version( "2.6" ) );
        assertPluginManagementPlugins( project.getModel(), -1 );
    }

    @Test
    public void adjustReportPluginWithWrongVersion_InheritFromToolchain()
        throws Throwable
    {
        final String toolchainPath = REPORTS_TOOLCHAIN_PATH;
        final String path = "child-reportPlugin-wrongVersion-1.0.pom";

        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );

        assertReportPlugins( original, 1, mavenPlugin( "maven-checkstyle-plugin" ).version( "2.2" ) );
        assertPluginManagementPlugins( original, -1 );

        final Project project =
            adjustSingle( "Adjust report plugin version in POM that inherits from toolchain", path, toolchainPath );

        assertReportPlugins( project.getModel(), 1, mavenPlugin( "maven-checkstyle-plugin" ).version( "2.6" ) );
        assertPluginManagementPlugins( project.getModel(), -1 );
    }

    @Test
    public void adjustReportPluginWithoutVersion_NotInheritFromToolchain()
        throws Throwable
    {
        final String toolchainPath = REPORTS_TOOLCHAIN_PATH;
        final String path = "external-withParent-reportPlugin-noVersion-1.0.pom";

        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );

        assertReportPlugins( original, 1, mavenPlugin( "maven-checkstyle-plugin" ).version( null ) );
        assertPluginManagementPlugins( original, -1 );

        final Project project =
            adjustSingle( "Adjust report plugin version in POM not inherited from toolchain", path, toolchainPath );

        assertReportPlugins( project.getModel(), 1, mavenPlugin( "maven-checkstyle-plugin" ).version( "2.6" ) );
        assertPluginManagementPlugins( project.getModel(), -1 );

        // NOTE: PluginManagement MUST be injected into the highest level possible!
        // Since all non-toolchain parent POMs MUST be built for the repository to be
        // complete, we can inject it there.
        // assertPluginManagementPlugins( project.getModel(), 1, mavenPlugin( "maven-checkstyle-plugin" ).version( "2.6"
        // ) );
    }

    @Test
    public void adjustReportPluginWithWrongVersion_NotInheritFromToolchain()
        throws Throwable
    {
        final String toolchainPath = REPORTS_TOOLCHAIN_PATH;
        final String path = "external-withParent-reportPlugin-wrongVersion-1.0.pom";

        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );

        assertReportPlugins( original, 1, mavenPlugin( "maven-checkstyle-plugin" ).version( "2.2" ) );
        assertPluginManagementPlugins( original, -1 );

        final Project project =
            adjustSingle( "Adjust report plugin version in POM not inherited from toolchain", path, toolchainPath );

        assertReportPlugins( project.getModel(), 1, mavenPlugin( "maven-checkstyle-plugin" ).version( "2.6" ) );
        assertPluginManagementPlugins( project.getModel(), -1 );

        // NOTE: PluginManagement MUST be injected into the highest level possible!
        // Since all non-toolchain parent POMs MUST be built for the repository to be
        // complete, we can inject it there.
        // assertPluginManagementPlugins( project.getModel(), 1, mavenPlugin( "maven-checkstyle-plugin" ).version( "2.6"
        // ) );
    }

    @Test
    public void removeBuildPlugin()
        throws Throwable
    {
        final String toolchainPath = EMPTY_TOOLCHAIN_PATH;
        final String path = "external-withParent-removePlugin-1.0.pom";

        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );

        assertBuildPlugins( original, 1, mavenPlugin( "maven-checkstyle-plugin" ) );
        assertPluginManagementPlugins( original, -1 );

        final Project project =
            adjustSingle( "Remove banned plugin from the build section in a POM", path, toolchainPath,
                          Collections.singletonList( "org.apache.maven.plugins:maven-checkstyle-plugin" ) );

        assertBuildPlugins( project.getModel(), -1 );
        assertPluginManagementPlugins( project.getModel(), -1 );
    }

    @Test
    public void removePluginManagementPlugin()
        throws Throwable
    {
        final String toolchainPath = EMPTY_TOOLCHAIN_PATH;
        final String path = "external-withParent-removeManagedPlugin-1.0.pom";

        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );

        assertPluginManagementPlugins( original, 1, mavenPlugin( "maven-checkstyle-plugin" ) );
        assertBuildPlugins( original, -1 );

        final Project project =
            adjustSingle( "Remove banned plugin from the pluginManagement section in a POM", path, toolchainPath,
                          Collections.singletonList( "org.apache.maven.plugins:maven-checkstyle-plugin" ) );

        assertBuildPlugins( project.getModel(), -1 );
        assertPluginManagementPlugins( project.getModel(), -1 );
    }

    @Test
    public void removeReportPlugin()
        throws Throwable
    {
        final String toolchainPath = EMPTY_TOOLCHAIN_PATH;
        final String path = "external-withParent-removeReportPlugin-1.0.pom";

        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );

        assertReportPlugins( original, 1, mavenPlugin( "maven-checkstyle-plugin" ) );
        assertPluginManagementPlugins( original, -1 );

        final Project project =
            adjustSingle( "Remove banned plugin from the reporting section in a POM", path, toolchainPath,
                          Collections.singletonList( "org.apache.maven.plugins:maven-checkstyle-plugin" ) );

        assertReportPlugins( project.getModel(), -1 );
        assertPluginManagementPlugins( project.getModel(), -1 );
    }

    // Everything in the system should have a toolchain ancestor...
    // If the POM doesn't have a parent, it has the toolchain injected as its parent
    // If it does have a parent, the above rule ensures that parent is descended from the toolchain.
    //
    // Because of this, any new plugin executions can be specified ONLY in the toolchain POM.
    // @Test
    // public void addPluginExecutionInChildNotInheritingFromToolchain()
    // throws Throwable
    // {
    // String toolchainPath = ADDITIONS_TOOLCHAIN_PATH;
    // String path = "external-withParent-noPlugins-1.0.pom";
    //
    // Model original = loadModel( TOOLCHAIN_TEST_POMS + path );
    //
    // assertBuildPlugins( original, -1 );
    // assertPluginManagementPlugins( original, -1 );
    //
    // Project project =
    // adjustSingle( "Inject single plugin execution into POM not inheriting from "
    // + "toolchain that doesn't already have it", path, toolchainPath );
    //
    // assertBuildPlugins( project.getModel(), 1, mavenPlugin( "maven-source-plugin" ).version( null ) );
    //
    // assertPluginManagementPlugins( project.getModel(),
    // 1,
    // mavenPlugin( "maven-source-plugin" ).version( "2.1.2" ).execution( "create-source-jar" ) );
    // }

    @Test
    public void addPluginExecutionToExistingExecutionsInChildNotInheritingFromToolchain()
        throws Throwable
    {
        final String toolchainPath = ADDITIONS_TOOLCHAIN_PATH;
        final String path = "external-withParent-existingPluginExecution-1.0.pom";

        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );

        assertBuildPlugins( original, 1, mavenPlugin( "maven-source-plugin" ).version( "2.1.2" )
                                                                             .execution( "other-source-jar" ) );

        assertPluginManagementPlugins( original, -1 );

        final Project project =
            adjustSingle( "Inject plugin execution into POM not inheriting from "
                + "toolchain that already has another execution of the plugin", path, toolchainPath );

        assertBuildPlugins( project.getModel(), 1, mavenPlugin( "maven-source-plugin" ).version( null )
                                                                                       .execution( "other-source-jar" ) );

        assertPluginManagementPlugins( project.getModel(), -1 );

        // NOTE: PluginManagement MUST be injected into the highest level possible!
        // Since all non-toolchain parent POMs MUST be built for the repository to be
        // complete, we can inject it there.
        // assertPluginManagementPlugins( project.getModel(),
        // 1,
        // mavenPlugin( "maven-source-plugin" ).version( "2.1.2" ).execution( "create-source-jar" ) );
    }

    @Test
    public void doNothingToCollidingPluginExecutionInChildNotInheritingFromToolchain()
        throws Throwable
    {
        final String toolchainPath = ADDITIONS_TOOLCHAIN_PATH;
        final String path = "external-withParent-collidingPluginExecution-1.0.pom";

        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );

        assertBuildPlugins( original, 1, mavenPlugin( "maven-source-plugin" ).version( "2.1.2" )
                                                                             .execution( "create-source-jar" ) );

        assertPluginManagementPlugins( original, -1 );

        final Project project =
            adjustSingle( "Leave existing plugin execution in POM not inheriting from "
                + "toolchain when it collides with the injected one", path, toolchainPath );

        assertBuildPlugins( project.getModel(), 1, mavenPlugin( "maven-source-plugin" ).version( null )
                                                                                       .execution( "create-source-jar" ) );

        assertPluginManagementPlugins( project.getModel(), -1 );

        // NOTE: PluginManagement MUST be injected into the highest level possible!
        // Since all non-toolchain parent POMs MUST be built for the repository to be
        // complete, we can inject it there.
        // assertPluginManagementPlugins( project.getModel(), 1, mavenPlugin( "maven-source-plugin" ).version( "2.1.2" )
        // );
    }

    @Test
    public void adjustInheritedToolchainParentVersion()
        throws Throwable
    {
        final String path = "child-wrongParentVersion-1.0.pom";

        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );
        final Model toolchain = loadModel( TOOLCHAIN_PATH );

        assertParent( original, null, null, toolchain.getVersion(), false );

        final Project project = adjustSingle( "Adjust parent version in POM inherited from toolchain", path );

        assertParent( project.getModel(), null, null, toolchain.getVersion(), true );
    }

    @Test
    public void injectToolchainAsParent()
        throws Throwable
    {
        final String path = "external-noParent-1.0.pom";

        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );
        final Model toolchain = loadModel( TOOLCHAIN_PATH );

        assertThat( original.getParent(), nullValue() );

        final Project project = adjustSingle( "Inject toolchain as parent in POM without a pre-existing parent", path );

        assertThat( project.getModel(), notNullValue() );
        assertParent( project.getModel(), null, null, toolchain.getVersion(), true );
    }

    @Test
    public void doNotInjectToolchainWhenPomHasExternalParent()
        throws Throwable
    {
        final String path = "external-withParent-1.0.pom";

        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );
        final Model toolchain = loadModel( TOOLCHAIN_PATH );

        assertParent( original, toolchain.getGroupId(), toolchain.getArtifactId(), toolchain.getVersion(), false );

        adjustNone( "Inject toolchain as parent in POM without a pre-existing parent", path, TOOLCHAIN_PATH, null );

        // NOTE: PluginManagement MUST be injected into the highest level possible!
        // Since all non-toolchain parent POMs MUST be built for the repository to be
        // complete, we can inject it there.
        // adjustSingle( "Inject toolchain as parent in POM without a pre-existing parent", path );
        //
        // assertThat( project.getModel(), notNullValue() );
        // assertParent( project.getModel(), toolchain.getGroupId(), toolchain.getArtifactId(), toolchain.getVersion(),
        // false );
    }

    @Test
    public void adjustNonManagedPluginForInheritedToolchain()
        throws Throwable
    {
        final Project project =
            adjustSingle( "Adjust single non-managed plugin in POM inherited from toolchain",
                          "child-nonManaged-1.0.pom" );

        final Model changed = project.getModel();

        final Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        final List<Plugin> plugins = build.getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );

        final Plugin plugin = plugins.get( 0 );
        assertThat( plugin.getArtifactId(), equalTo( "maven-compiler-plugin" ) );
        assertThat( plugin.getVersion(), nullValue() );
    }

    @Test
    public void adjustManagedPluginForInheritedToolchain()
        throws Throwable
    {
        final Project project =
            adjustSingle( "Adjust managed plugin in POM inherited from toolchain", "child-managed-1.0.pom" );

        final Model changed = project.getModel();

        final Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        final PluginManagement pm = build.getPluginManagement();
        assertThat( pm, notNullValue() );

        final List<Plugin> plugins = pm.getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );

        final Plugin plugin = plugins.get( 0 );
        assertThat( plugin.getArtifactId(), equalTo( "maven-compiler-plugin" ) );
        assertThat( plugin.getVersion(), nullValue() );
    }

    @Test
    public void adjustNonManagedPluginForNonInheritedToolchain()
        throws Throwable
    {
        final Project project =
            adjustSingle( "Adjust non-managed plugin in POM NOT inheriting from toolchain",
                          "external-withParent-nonManaged-1.0.pom" );

        final Model changed = project.getModel();
        System.out.println( "Verifying POM: " + project.getPom() );
        LoggingFixture.flushLogging();

        final Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        final List<Plugin> plugins = build.getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );

        final Plugin plugin = plugins.get( 0 );
        assertThat( plugin.getArtifactId(), equalTo( "maven-compiler-plugin" ) );
        assertThat( plugin.getVersion(), nullValue() );

        // NOTE: PluginManagement MUST be injected into the highest level possible!
        // Since all non-toolchain parent POMs MUST be built for the repository to be
        // complete, we can inject it there.
        // PluginManagement pm = build.getPluginManagement();
        // assertThat( pm, notNullValue() );
        //
        // Map<String, Plugin> pluginMap = pm.getPluginsAsMap();
        // assertThat( pluginMap, notNullValue() );
        //
        // plugin = pluginMap.get( "org.apache.maven.plugins:maven-compiler-plugin" );
        // assertThat( plugin.getVersion(), equalTo( "2.0" ) );
    }

    @Test
    public void adjustNonManagedPluginForInjectedToolchainParent()
        throws Throwable
    {
        final Project project =
            adjustSingle( "Adjust single non-managed plugin in POM to be inherited from toolchain",
                          "external-noParent-nonManaged-1.0.pom" );

        final Model changed = project.getModel();

        final Parent parent = changed.getParent();
        assertThat( parent, notNullValue() );

        final FullProjectKey tk = getToolchainKey();
        assertThat( parent.getGroupId(), equalTo( tk.getGroupId() ) );
        assertThat( parent.getArtifactId(), equalTo( tk.getArtifactId() ) );
        assertThat( parent.getVersion(), equalTo( tk.getVersion() ) );

        final Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        final List<Plugin> plugins = build.getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );

        final Plugin plugin = plugins.get( 0 );
        assertThat( plugin.getArtifactId(), equalTo( "maven-compiler-plugin" ) );
        assertThat( plugin.getVersion(), nullValue() );
    }

    @Test
    public void removeEmptyNonManagedPluginForInheritedToolchain()
        throws Throwable
    {
        final Project project =
            adjustSingle( "Adjust single non-managed plugin in POM inherited from toolchain",
                          "child-nonManaged-emptyPlugin-1.0.pom" );

        final Model changed = project.getModel();

        final Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        final List<Plugin> plugins = build.getPlugins();
        assertThat( plugins == null || plugins.isEmpty(), is( true ) );
    }

    @Test
    public void removeEmptyNonManagedPluginForInjectedToolchainParent()
        throws Throwable
    {
        final Project project =
            adjustSingle( "Adjust single non-managed plugin in POM inherited from toolchain",
                          "external-noParent-nonManaged-emptyPlugin-1.0.pom" );

        final Model changed = project.getModel();

        final Parent parent = changed.getParent();
        assertThat( parent, notNullValue() );

        final FullProjectKey tk = getToolchainKey();
        assertThat( parent.getGroupId(), equalTo( tk.getGroupId() ) );
        assertThat( parent.getArtifactId(), equalTo( tk.getArtifactId() ) );
        assertThat( parent.getVersion(), equalTo( tk.getVersion() ) );

        final Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        final List<Plugin> plugins = build.getPlugins();
        assertThat( plugins == null || plugins.isEmpty(), is( true ) );
    }

    @Test
    public void removeEmptyManagedPluginForInheritedToolchain()
        throws Throwable
    {
        final Project project =
            adjustSingle( "Adjust managed plugin in POM inherited from toolchain", "child-managed-emptyPlugin-1.0.pom" );

        final Model changed = project.getModel();

        final Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        final PluginManagement pm = build.getPluginManagement();
        assertThat( pm, notNullValue() );

        final List<Plugin> plugins = pm.getPlugins();
        assertThat( plugins == null || plugins.isEmpty(), is( true ) );
    }

    @Test
    public void removeEmptyNonManagedPluginForNonInheritedToolchain()
        throws Throwable
    {
        final Project project =
            adjustSingle( "Adjust non-managed plugin in POM NOT inheriting from toolchain",
                          "external-withParent-nonManaged-emptyPlugin-1.0.pom" );

        final Model changed = project.getModel();

        final Build build = changed.getBuild();
        assertThat( build, notNullValue() );

        final List<Plugin> plugins = build.getPlugins();
        assertThat( plugins == null || plugins.isEmpty(), is( true ) );

        // NOTE: PluginManagement MUST be injected into the highest level possible!
        // Since all non-toolchain parent POMs MUST be built for the repository to be
        // complete, we can inject it there.
        // PluginManagement pm = build.getPluginManagement();
        // assertThat( pm, notNullValue() );
        //
        // Map<String, Plugin> pluginMap = pm.getPluginsAsMap();
        // assertThat( pluginMap, notNullValue() );
        //
        // Plugin plugin = pluginMap.get( "org.apache.maven.plugins:maven-compiler-plugin" );
        // assertThat( plugin.getVersion(), equalTo( "2.0" ) );
    }

    private Project adjustSingle( final String description, final String pomPath )
        throws Throwable
    {
        return adjustSingle( description, pomPath, TOOLCHAIN_PATH, null );
    }

    private Project adjustSingle( final String description, final String pomPath, final String toolchainPath )
        throws Throwable
    {
        return adjustSingle( description, pomPath, toolchainPath, null );
    }

    private Project adjustSingle( final String description, final String pomPath, final String toolchainPath,
                                  final List<String> removedPlugins )
        throws Throwable
    {
        try
        {
            System.out.println( "ADJUSTING: " + description + "\nPOM: " + pomPath + "\nToolchain: " + toolchainPath );

            final File srcPom = getResourceFile( TOOLCHAIN_TEST_POMS + pomPath );
            final String toolchain = getResourceFile( toolchainPath ).getAbsolutePath();

            final File pom = new File( repo, srcPom.getName() );
            copyFile( srcPom, pom );

            final VersionManagerSession session = newVersionManagerSession
                            ( workspace, reports, null, removedPlugins, 
                              Collections.<String> emptyList() );

            final File remoteRepo = getResourceFile( TOOLCHAIN_TEST_POMS + "repo" );
            session.setRemoteRepository( remoteRepo.toURI()
                                                   .normalize()
                                                   .toURL()
                                                   .toExternalForm() );

            final Set<File> modified = vman.modifyVersions( pom, null, toolchain, session );
            assertNoErrors( session );

            final Set<Model> changedModels = loadModels( modified );
            assertThat( "POM: " + pomPath + " was not modified!", changedModels.size(), equalTo( 1 ) );

            final Model model = changedModels.iterator()
                                             .next();
            dumpModel( model );

            return new Project( pom, model );
        }
        catch ( final Throwable t )
        {
            t.printStackTrace();
            throw t;
        }
    }

    private void adjustNone( final String description, final String pomPath, final String toolchainPath,
                             final List<String> removedPlugins )
        throws Throwable
    {
        try
        {
            System.out.println( "ADJUSTING: " + description + "\nPOM: " + pomPath + "\nToolchain: " + toolchainPath );

            final File srcPom = getResourceFile( TOOLCHAIN_TEST_POMS + pomPath );
            final String toolchain = getResourceFile( toolchainPath ).getAbsolutePath();

            final File pom = new File( repo, srcPom.getName() );
            copyFile( srcPom, pom );

            final VersionManagerSession session = newVersionManagerSession
                            ( workspace, reports, null, removedPlugins, 
                              Collections.<String> emptyList() );

            final File remoteRepo = getResourceFile( TOOLCHAIN_TEST_POMS + "repo" );
            session.setRemoteRepository( remoteRepo.toURI()
                                                   .normalize()
                                                   .toURL()
                                                   .toExternalForm() );

            final Set<File> modified = vman.modifyVersions( pom, null, toolchain, session );
            assertNoErrors( session );

            final Set<Model> changedModels = loadModels( modified );
            assertThat( "POM: " + pomPath + " was modified!", changedModels.size(), equalTo( 0 ) );
        }
        catch ( final Throwable t )
        {
            t.printStackTrace();
            throw t;
        }
    }

    private static FullProjectKey getToolchainKey()
        throws IOException, ProjectToolsException
    {
        if ( toolchainKey == null )
        {
            toolchainKey = loadProjectKey( TOOLCHAIN_PATH );
        }

        return toolchainKey;
    }

    private void assertParent( final Model model, final String groupId, final String artifactId, final String version,
                               final boolean matches )
    {
        assertThat( "Model is null.", model, notNullValue() );

        assertThat( "Parent is null.", model.getParent(), notNullValue() );

        if ( matches )
        {
            if ( groupId != null )
            {
                assertThat( "Parent has wrong groupId.", model.getParent()
                                                              .getGroupId(), equalTo( groupId ) );
            }

            if ( artifactId != null )
            {
                assertThat( "Parent has wrong artifactId.", model.getParent()
                                                                 .getArtifactId(), equalTo( artifactId ) );
            }

            if ( version != null )
            {
                assertThat( "Parent has wrong version.", model.getParent()
                                                              .getVersion(), equalTo( version ) );
            }
        }
        else
        {
            if ( groupId != null )
            {
                assertThat( "Parent has wrong groupId.", model.getParent()
                                                              .getGroupId(), not( equalTo( groupId ) ) );
            }

            if ( artifactId != null )
            {
                assertThat( "Parent has wrong artifactId.", model.getParent()
                                                                 .getArtifactId(), not( equalTo( artifactId ) ) );
            }

            if ( version != null )
            {
                assertThat( "Parent has wrong version.", model.getParent()
                                                              .getVersion(), not( equalTo( version ) ) );
            }
        }
    }

    private void assertBuildPlugins( final Model model, final int pluginCount, final PluginMatcher... pluginCheckSet )
    {
        if ( pluginCount < 1 && model.getBuild() == null )
        {
            return;
        }

        final Build build = model.getBuild();
        assertThat( "Build section is missing!", build, notNullValue() );

        assertPlugins( build, pluginCount, pluginCheckSet );
    }

    private void assertPluginManagementPlugins( final Model model, final int pluginCount,
                                                final PluginMatcher... pluginCheckSet )
    {
        if ( pluginCount < 1 && ( model.getBuild() == null || model.getBuild()
                                                                   .getPluginManagement() == null ) )
        {
            return;
        }

        final Build build = model.getBuild();
        assertThat( "Build section is missing!", build, notNullValue() );

        final PluginManagement pm = build.getPluginManagement();
        assertThat( "PluginManagement section is missing!", pm, notNullValue() );

        assertPlugins( pm, pluginCount, pluginCheckSet );
    }

    private void assertPlugins( final PluginContainer pluginContainer, final int pluginCount,
                                final PluginMatcher... pluginCheckSet )
    {
        if ( pluginCount > 0 )
        {
            final List<Plugin> plugins = pluginContainer.getPlugins();
            assertThat( plugins, notNullValue() );
            assertThat( plugins.size(), equalTo( pluginCount ) );

            if ( pluginCheckSet != null )
            {
                final Map<String, Plugin> pluginsAsMap = pluginContainer.getPluginsAsMap();
                System.out.println( "Got plugins:\n\n" + join( pluginsAsMap.values(), "\n  " ) );
                for ( final PluginMatcher checks : pluginCheckSet )
                {
                    final Plugin plugin = pluginsAsMap.get( checks.key() );
                    assertThat( checks.key() + " is missing.", plugin, notNullValue() );

                    if ( checks.v() != PluginMatcher.UNSPECIFIED_VERSION )
                    {
                        assertThat( plugin.getVersion(), equalTo( checks.v() ) );
                    }

                    if ( checks.eids() != null )
                    {
                        final List<PluginExecution> executions = plugin.getExecutions();
                        assertThat( executions, notNullValue() );
                        assertThat( executions.size(), equalTo( checks.eids()
                                                                      .size() ) );

                        for ( final PluginExecution pe : executions )
                        {
                            assertThat( "Plugin execution: " + pe.getId() + " not allowed!", checks.eids(),
                                        hasItem( pe.getId() ) );
                            checks.eids()
                                  .remove( pe.getId() );
                        }

                        for ( final String eid : checks.eids() )
                        {
                            fail( "Plugin: " + checks.key() + " execution: " + eid + " was not found!" );
                        }
                    }
                }
            }
        }
        else
        {
            if ( pluginContainer != null && pluginContainer.getPlugins() != null )
            {
                assertThat( "There should be no plugins!", pluginContainer.getPlugins()
                                                                          .size(), equalTo( 0 ) );
            }
        }
    }

    private void assertReportPlugins( final Model model, final int pluginCount, final PluginMatcher... pluginCheckSet )
    {
        if ( pluginCount < 1 && model.getBuild() == null )
        {
            return;
        }

        final Reporting reporting = model.getReporting();
        assertThat( "Reporting section is missing!", reporting, notNullValue() );

        if ( pluginCount > 0 )
        {
            final List<ReportPlugin> plugins = reporting.getPlugins();
            assertThat( plugins, notNullValue() );
            assertThat( plugins.size(), equalTo( pluginCount ) );

            if ( pluginCheckSet != null )
            {
                final Map<String, ReportPlugin> pluginsAsMap = reporting.getReportPluginsAsMap();
                System.out.println( "Got report plugins:\n\n" + join( pluginsAsMap.values(), "\n  " ) );

                for ( final PluginMatcher checks : pluginCheckSet )
                {
                    final ReportPlugin plugin = pluginsAsMap.get( checks.key() );
                    assertThat( plugin, notNullValue() );

                    if ( checks.v() != PluginMatcher.UNSPECIFIED_VERSION )
                    {
                        assertThat( plugin.getVersion(), equalTo( checks.v() ) );
                    }

                    if ( checks.eids() != null )
                    {
                        final List<ReportSet> reportSets = plugin.getReportSets();
                        assertThat( reportSets, notNullValue() );
                        assertThat( reportSets.size(), equalTo( checks.eids()
                                                                      .size() ) );

                        for ( final ReportSet rs : reportSets )
                        {
                            assertThat( "Plugin execution: " + rs.getId() + " not allowed!", checks.eids(),
                                        hasItem( rs.getId() ) );
                            checks.eids()
                                  .remove( rs.getId() );
                        }

                        for ( final String eid : checks.eids() )
                        {
                            fail( "Plugin: " + checks.key() + " execution: " + eid + " was not found!" );
                        }
                    }
                }
            }
        }
        else
        {
            if ( reporting != null && reporting.getPlugins() != null )
            {
                assertThat( "There should be no report plugins!", reporting.getPlugins()
                                                                           .size(), equalTo( 0 ) );
            }
        }
    }

}
