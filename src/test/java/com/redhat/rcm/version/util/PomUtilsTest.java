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

package com.redhat.rcm.version.util;

import static com.redhat.rcm.version.testutil.TestProjectUtils.getResourceFile;
import static com.redhat.rcm.version.testutil.TestProjectUtils.loadModel;
import static com.redhat.rcm.version.testutil.TestProjectUtils.newVersionManagerSession;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.redhat.rcm.version.fixture.LoggingFixture;
import com.redhat.rcm.version.mgr.AbstractVersionManagerTest;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

import java.io.File;
import java.util.List;

public class PomUtilsTest
    extends AbstractVersionManagerTest
{
    private static final String BASE = "pom-formats/";

    @Rule
    public final TestName name = new TestName();

    @Test
    public void pomRewritePreservesXMLAttributesInPluginConfiguration()
        throws Exception
    {
        final File srcPom = getResourceFile( BASE + "plugin-config-attributes.pom" );

        final File pom = new File( repo, srcPom.getName() );
        FileUtils.copyFile( srcPom, pom );

        final Model model = loadModel( pom );

        assertThat( model.getBuild(), notNullValue() );

        List<Plugin> plugins = model.getBuild().getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );

        Plugin plugin = plugins.get( 0 );
        Object config = plugin.getConfiguration();
        assertThat( config, notNullValue() );

        assertThat( config.toString().contains( "<delete dir=\"foobar\"" ), equalTo( true ) );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        final File out =
            PomUtils.writeModifiedPom( model,
                                       pom,
                                       new FullProjectKey( model ),
                                       model.getVersion(),
                                       pom.getParentFile(),
                                       session,
                                       false );

        assertNoErrors( session );
        final String pomStr = readFileToString( out );
        System.out.println( "Modified POM for " + name.getMethodName() + ":\n\n" + pomStr + "\n\n" );

        final Model changed = loadModel( out );

        assertThat( changed.getBuild(), notNullValue() );

        plugins = changed.getBuild().getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );

        plugin = plugins.get( 0 );
        config = plugin.getConfiguration();
        assertThat( config, notNullValue() );
        assertThat( config.toString().contains( "<delete dir=\"foobar\"" ), equalTo( true ) );
    }

    @Test
    public void pomRewriteInjectsXMLDeclaration()
        throws Exception
    {
        final File srcPom = getResourceFile( BASE + "no-xml-declaration.pom" );

        final File pom = new File( repo, srcPom.getName() );
        FileUtils.copyFile( srcPom, pom );

        final String orig = readFileToString( pom );
        assertThat( orig.contains( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" ), equalTo( false ) );

        final Model model = loadModel( pom );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        final File out =
            PomUtils.writeModifiedPom( model,
                                       pom,
                                       new FullProjectKey( model ),
                                       model.getVersion(),
                                       pom.getParentFile(),
                                       session,
                                       false );

        assertNoErrors( session );
        final String pomStr = readFileToString( out );
        System.out.println( "Modified POM for " + name.getMethodName() + ":\n\n" + pomStr + "\n\n" );
        assertThat( pomStr.contains( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" ), equalTo( true ) );

        loadModel( out );
    }

    @Test
    public void pomRewriteInjectsSchemaAttributes()
        throws Exception
    {
        final File srcPom = getResourceFile( BASE + "no-schema-attributes.pom" );

        final File pom = new File( repo, srcPom.getName() );
        FileUtils.copyFile( srcPom, pom );

        final String orig = readFileToString( pom );

        assertThat( orig.contains( "xmlns=\"http://maven.apache.org/POM/4.0.0\"" ), equalTo( false ) );
        assertThat( orig.contains( "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" ), equalTo( false ) );
        assertThat( orig.contains( "xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\"" ),
                    equalTo( false ) );

        final Model model = loadModel( pom );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        final File out =
            PomUtils.writeModifiedPom( model,
                                       pom,
                                       new FullProjectKey( model ),
                                       model.getVersion(),
                                       pom.getParentFile(),
                                       session,
                                       false );

        assertNoErrors( session );
        final String pomStr = readFileToString( out );
        System.out.println( "Modified POM for " + name.getMethodName() + ":\n\n" + pomStr + "\n\n" );

        assertThat( pomStr.contains( "xmlns=\"http://maven.apache.org/POM/4.0.0\"" ), equalTo( true ) );
        assertThat( pomStr.contains( "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" ), equalTo( true ) );
        assertThat( pomStr.contains( "xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\"" ),
                    equalTo( true ) );

        // make sure we're not stranding all the other elements to be in some different, empty xmlns.
        assertThat( "Found empty xmlns! All non-plugin-config elements should be aligned to project element...",
                    pomStr.contains( "xmlns=\"\"" ),
                    equalTo( false ) );

        loadModel( out );
    }

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

        System.out.println( "START: " + name.getMethodName() + "\n\n" );
    }

    @After
    public void teardown()
    {
        System.out.println( "\n\nEND: " + name.getMethodName() );
    }

    @AfterClass
    public static void shutdownLogging()
    {
        LoggingFixture.flushLogging();
    }
}
