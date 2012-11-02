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

package com.redhat.rcm.version.mgr.mod;

import static com.redhat.rcm.version.testutil.TestProjectUtils.loadModel;
import static com.redhat.rcm.version.testutil.TestProjectUtils.loadProject;
import static com.redhat.rcm.version.testutil.TestProjectUtils.newVersionManagerSession;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.Collections;

import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.redhat.rcm.version.fixture.LoggingFixture;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.testutil.SessionBuilder;
import com.redhat.rcm.version.testutil.TestVersionManager;

public class ExtensionsRemovalModderTest extends AbstractModderTest
{
    private static final String BASE = "extensions/";

    protected File repo;

    protected File workspace;

    protected File reports;

    private TestVersionManager testVman;

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void logging()
    {
        LoggingFixture.setupLogging();
    }

    @Before
    public void setup()
        throws Exception
    {
        testVman = TestVersionManager.getInstance();

        if ( repo == null )
        {
            repo = tempFolder.newFolder( "repository" );
        }

        if ( workspace == null )
        {
            workspace = tempFolder.newFolder( "workspace" );
        }

        if ( reports == null )
        {
            reports = tempFolder.newFolder( "reports" );
        }
    }

    @Test
    public void removeExtensions()
        throws Exception
    {
        final Model model = loadModel( BASE + "pom-removable.xml" );

        assertThat( model.getBuild()
                         .getExtensions()
                         .size(), equalTo( 1 ) );

        final boolean changed =
            testVman.getModder( ExtensionsRemovalModder.class )
                    .inject( new Project( model ), newVersionManagerSession( workspace, reports, "-rebuild-1" ) );

        assertThat( changed, equalTo( true ) );
        assertThat( model.getBuild()
                         .getExtensions()
                         .size(), equalTo( 0 ) );

    }

    @Test
    public void preserveWhitelistedExtensionWithDirectVersionProperty()
        throws Exception
    {
        final Project project = loadProject( BASE + "pom-whitelisted-with-prop.xml" );

        System.out.println( "Model parent is: " + project.getParent() );
        final Model model = project.getModel();

        assertThat( model.getBuild()
                         .getExtensions()
                         .size(), equalTo( 1 ) );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withExtensionsWhitelist( Collections.singletonList( "org.foo:bar-ext" ) )
                                                    .build();

        final Project toolchain = loadProject( BASE + "toolchain-with-prop.pom" );
        final MavenProject toolchainM = new MavenProject( toolchain.getModel() );
        toolchainM.setFile( toolchain.getPom() );
        toolchainM.setOriginalModel( toolchain.getModel() );

        session.setToolchain( toolchain.getPom(), toolchainM );

        final boolean changed = testVman.getModder( ExtensionsRemovalModder.class )
                                        .inject( project, session );

        assertThat( changed, equalTo( false ) );
        assertThat( model.getBuild()
                         .getExtensions()
                         .size(), equalTo( 1 ) );

        final Extension ext = model.getBuild()
                                   .getExtensions()
                                   .get( 0 );
        assertThat( ext.getVersion(), equalTo( "${version.org.foo-bar-ext}" ) );
    }

    @Test
    public void preserveWhitelistedExtensionWithGeneratedVersionProperty()
        throws Exception
    {
        final Project project = loadProject( BASE + "pom-whitelisted-no-prop.xml" );

        System.out.println( "Model parent is: " + project.getParent() );
        final Model model = project.getModel();

        assertThat( model.getBuild()
                         .getExtensions()
                         .size(), equalTo( 1 ) );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withExtensionsWhitelist( Collections.singletonList( "org.foo:bar-ext" ) )
                                                    .build();

        final Project toolchain = loadProject( BASE + "toolchain-no-prop.pom" );
        final MavenProject toolchainM = new MavenProject( toolchain.getModel() );
        toolchainM.setFile( toolchain.getPom() );
        toolchainM.setOriginalModel( toolchain.getModel() );

        session.setToolchain( toolchain.getPom(), toolchainM );

        final boolean changed = testVman.getModder( ExtensionsRemovalModder.class )
                                        .inject( project, session );

        assertThat( changed, equalTo( false ) );
        assertThat( model.getBuild()
                         .getExtensions()
                         .size(), equalTo( 1 ) );

        final Extension ext = model.getBuild()
                                   .getExtensions()
                                   .get( 0 );
        assertThat( ext.getVersion(), equalTo( "${versionmapper.org.foo-bar-ext}" ) );
    }
}
