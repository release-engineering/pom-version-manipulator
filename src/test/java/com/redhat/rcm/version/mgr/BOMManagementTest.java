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

package com.redhat.rcm.version.mgr;

import static com.redhat.rcm.version.testutil.TestProjectUtils.getResourceFile;
import static com.redhat.rcm.version.testutil.TestProjectUtils.loadModel;
import static com.redhat.rcm.version.testutil.TestProjectUtils.newVersionManagerSession;
import static com.redhat.rcm.version.testutil.VManAssertions.assertNormalizedToBOMs;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.codehaus.plexus.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.redhat.rcm.version.fixture.LoggingFixture;
import com.redhat.rcm.version.mgr.mod.BomModder;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.testutil.SessionBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public class BOMManagementTest
    extends AbstractVersionManagerTest
{

    @Rule
    public TestName name = new TestName();

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
        LoggingFixture.flushLogging();
        System.out.println( "\n\nEND: " + name.getMethodName() );
    }

    @Test
    public void modifyProjectTree_BOMInjected()
        throws Exception
    {
        final File srcRepo = getResourceFile( "bom-injection-multi" );
        FileUtils.copyDirectoryStructure( srcRepo, new File( repo, "project" ) );

        final File pom = new File( repo, "project/pom.xml" );

        final File bom = getResourceFile( "bom.xml" );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final Set<File> modified =
            vman.modifyVersions( pom, Collections.singletonList( bom.getAbsolutePath() ), null, session );

        assertNoErrors( session );

        // NOTE: Child POM not modified...nothing to do there!
        assertThat( modified.size(), equalTo( 1 ) );
        assertNormalizedToBOMs( modified, Collections.singleton( bom ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePom_BOMInjected()
        throws Exception
    {
        final File srcPom = getResourceFile( "bom-injection-single/pom.xml" );
        final File bom = getResourceFile( "bom.xml" );

        final File pom = new File( repo, srcPom.getName() );
        FileUtils.copyFile( srcPom, pom );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final Set<File> modified =
            vman.modifyVersions( pom, Collections.singletonList( bom.getAbsolutePath() ), null, session );

        assertNoErrors( session );
        assertThat( modified.size(), equalTo( 1 ) );
        assertNormalizedToBOMs( modified, Collections.singleton( bom ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePom_BOMWithParentInRepo()
        throws Exception
    {
        final File srcRepo = getResourceFile( "bom-parent-in-repo" );
        FileUtils.copyDirectoryStructure( srcRepo, repo );

        final File pom = new File( repo, "project/pom.xml" );
        final File bom = new File( repo, "bom.xml" );
        final File remoteRepo = new File( repo, "repo" );

        final Repository resolve = new Repository();

        resolve.setId( "vman" );
        resolve.setUrl( remoteRepo.toURI().normalize().toURL().toExternalForm() );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        session.setResolveRepositories( resolve );

        final Set<File> modified =
            vman.modifyVersions( pom, Collections.singletonList( bom.getAbsolutePath() ), null, session );
        assertNoErrors( session );
        assertNormalizedToBOMs( modified, Collections.singleton( bom ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePom_BOMofBOMs()
        throws Exception
    {
        System.out.println( "BOM-of-BOMS test (normalize to BOM usage)..." );

        final File srcRepo = getResourceFile( "bom-of-boms" );
        FileUtils.copyDirectoryStructure( srcRepo, repo );

        final File pom = new File( repo, "project/pom.xml" );
        final File bom = new File( repo, "bom.xml" );
        final File remoteRepo = new File( repo, "repo" );

        final Repository resolve = new Repository();

        resolve.setId( "vman" );
        resolve.setUrl( remoteRepo.toURI().normalize().toURL().toExternalForm() );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        session.setResolveRepositories( resolve );

        final Set<File> modified =
            vman.modifyVersions( pom, Collections.singletonList( bom.getAbsolutePath() ), null, session );
        assertNoErrors( session );
        assertNormalizedToBOMs( modified, Collections.singleton( bom ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePom_NormalizeToBOMUsage()
        throws Exception
    {
        System.out.println( "Single POM test (normalize to BOM usage)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bom = getResourceFile( "bom.xml" );

        final File pom = new File( repo, srcPom.getName() );
        FileUtils.copyFile( srcPom, pom );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final Set<File> modified =
            vman.modifyVersions( pom, Collections.singletonList( bom.getAbsolutePath() ), null, session );
        assertNoErrors( session );
        assertNormalizedToBOMs( modified, Collections.singleton( bom ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyMultimodule_NormalizeToBOMUsage()
        throws Exception
    {
        System.out.println( "Mult-module project tree test (normalize to BOM usage)..." );

        final File srcRepo = getResourceFile( "project-dir" );
        final File bom = getResourceFile( "bom.xml" );

        FileUtils.copyDirectoryStructure( srcRepo, repo );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final Set<File> modified =
            vman.modifyVersions( repo, "pom.xml", "", Collections.singletonList( bom.getAbsolutePath() ), null, session );

        assertNoErrors( session );

        assertNormalizedToBOMs( modified, Collections.singleton( bom ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyMultimodule_IgnoreProjectInterdependency()
        throws Exception
    {
        System.out.println( "Multi-module tree with interdependencies test (normalize to BOM usage)..." );

        final File srcRepo = getResourceFile( "project-dir-with-interdep" );
        final File bom = getResourceFile( "bom.xml" );

        FileUtils.copyDirectoryStructure( srcRepo, repo );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final Set<File> modified =
            vman.modifyVersions( repo, "pom.xml", "", Collections.singletonList( bom.getAbsolutePath() ), null, session );

        assertNoErrors( session );

        final String g = "org.commonjava.rwx";

        final Set<Dependency> skipped =
            assertNormalizedToBOMs( modified,
                                    Collections.singleton( bom ),
                                    new VersionlessProjectKey( g, "rwx-parent" ),
                                    new VersionlessProjectKey( g, "rwx-core" ),
                                    new VersionlessProjectKey( g, "rwx-bindings" ),
                                    new VersionlessProjectKey( g, "rwx-http" ) );

        for ( final Dependency dep : skipped )
        {
            assertThat( "Dependency: " + dep + " should NOT be modified!", dep.getVersion(), notNullValue() );
        }

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyCompleteRepositoryVersions()
        throws IOException
    {
        System.out.println( "Complete repository test..." );

        final File srcRepo = getResourceFile( "repository" );
        final String bom = getResourceFile( "bom.xml" ).getAbsolutePath();

        FileUtils.copyDirectoryStructure( srcRepo, repo );

        modifyRepo( false, bom );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyRepositoryVersionsWithoutChangingTheRest()
        throws IOException
    {
        System.out.println( "Repository POM non-interference test..." );

        final File srcRepo = getResourceFile( "projects-with-property-refs" );
        final String bom = getResourceFile( "bom.xml" ).getAbsolutePath();

        FileUtils.copyDirectoryStructure( srcRepo, repo );

        final VersionManagerSession session = createVersionManagerSession();

        final Set<File> results =
            vman.modifyVersions( repo, "**/*.pom", "", Collections.singletonList( bom ), null, session );
        assertNoErrors( session );
        for ( final File file : results )
        {
            if ( "rwx-parent-0.2.1.pom".equals( file.getName() ) )
            {
                final String result = FileUtils.fileRead( file );
                assertTrue( "Non-dependency POM interpolation preserved in output!",
                            result.contains( "<finalName>${artifactId}</finalName>" ) );

                break;
            }
        }

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyPartialRepositoryVersions()
        throws IOException
    {
        System.out.println( "Partial repository test..." );

        final File srcRepo = getResourceFile( "repository.partial" );
        final String bom = getResourceFile( "bom.xml" ).getAbsolutePath();

        FileUtils.copyDirectoryStructure( srcRepo, repo );

        modifyRepo( false, bom );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyCompleteRepositoryVersions_UsingTwoBoms()
        throws IOException
    {
        System.out.println( "Complete repository test..." );

        final File srcRepo = getResourceFile( "repository" );
        final String bom1 = getResourceFile( "bom.part1.xml" ).getAbsolutePath();
        final String bom2 = getResourceFile( "bom.part2.xml" ).getAbsolutePath();

        FileUtils.copyDirectoryStructure( srcRepo, repo );

        modifyRepo( false, bom1, bom2 );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyPartialRepositoryVersions_UsingTwoBoms()
        throws IOException
    {
        System.out.println( "Partial repository test..." );

        final File srcRepo = getResourceFile( "repository.partial" );
        final String bom1 = getResourceFile( "bom.part1.xml" ).getAbsolutePath();
        final String bom2 = getResourceFile( "bom.part2.xml" ).getAbsolutePath();

        FileUtils.copyDirectoryStructure( srcRepo, repo );

        modifyRepo( false, bom1, bom2 );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePom()
        throws IOException
    {
        System.out.println( "Single POM test..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final String bom = getResourceFile( "bom.xml" ).getAbsolutePath();

        final File pom = new File( repo, srcPom.getName() );
        FileUtils.copyFile( srcPom, pom );

        final VersionManagerSession session = createVersionManagerSession();

        /* final File out = */vman.modifyVersions( pom, Collections.singletonList( bom ), getToolchainPath(), session );
        vman.generateReports( reports, session );

        // final String source = FileUtils.fileRead( srcPom );
        //
        // System.out.println( "Original source POM:\n\n" + source );
        //
        // final String result = FileUtils.fileRead( out );
        //
        // System.out.println( "Rewritten POM:\n\n" + result );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePomUsingInterpolatedBOM()
        throws IOException
    {
        System.out.println( "Single POM test (interpolated BOM)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final String bom = getResourceFile( "bom.interp.xml" ).getAbsolutePath();

        final File pom = new File( repo, srcPom.getName() );
        FileUtils.copyFile( srcPom, pom );

        final VersionManagerSession session = createVersionManagerSession();

        vman.modifyVersions( pom, Collections.singletonList( bom ), null, session );
        assertNoErrors( session );
        vman.generateReports( reports, session );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePomWithRelocations()
        throws IOException
    {
        System.out.println( "Single POM test (with relocations)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final String bom = getResourceFile( "bom-relocations.xml" ).getAbsolutePath();

        final File pom = new File( repo, srcPom.getName() );
        FileUtils.copyFile( srcPom, pom );

        final VersionManagerSession session = createVersionManagerSession();

        final Set<File> modified = vman.modifyVersions( pom, Collections.singletonList( bom ), null, session );
        assertNoErrors( session );

        assertNotNull( modified );
        assertThat( modified.size(), equalTo( 1 ) );

        final File out = modified.iterator().next();
        vman.generateReports( reports, session );

        final String result = FileUtils.fileRead( out );
        assertFalse( result.contains( "<groupId>commons-codec</groupId>" ) );
        assertFalse( result.contains( "<groupId>commons-lang</groupId>" ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePomWithNonBOMRelocatedCoordinates()
        throws IOException, ProjectToolsException
    {
        System.out.println( "Single POM test (with relocations NOT from BOM)..." );

        final File pom = getResourceFile( "pom-with-relocation.xml" );
        final String bom = getResourceFile( "bom-min.xml" ).getAbsolutePath();

        final Model model = loadModel( pom );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "old.group.id:some-artifact:1",
                                                                               "new.group.id:new-artifact:1.0.0" )
                                                    .build();

        vman.configureSession( Collections.singletonList( bom ), bom, session );

        new BomModder().inject( new Project( model ), session );

        assertNoErrors( session );

        assertThat( model.getDependencies().size(), equalTo( 1 ) );

        final Dependency dep = model.getDependencies().get( 0 );

        assertThat( dep.getGroupId(), equalTo( "new.group.id" ) );
        assertThat( dep.getArtifactId(), equalTo( "new-artifact" ) );
        assertThat( dep.getVersion(), nullValue() );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePomWithNonBOMRelocatedCoordinatesWhenDepNotInBOM()
        throws IOException, ProjectToolsException
    {
        System.out.println( "Single POM test (with relocations NOT from BOM, no dep in BOM)..." );

        final File pom = getResourceFile( "pom-with-relocation.xml" );
        final String bom = getResourceFile( "bom-empty.xml" ).getAbsolutePath();

        final Model model = loadModel( pom );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "old.group.id:some-artifact:1",
                                                                               "new.group.id:new-artifact:1.0.0" )
                                                    .build();

        vman.configureSession( Collections.singletonList( bom ), bom, session );

        new BomModder().inject( new Project( model ), session );

        assertNoErrors( session );

        assertThat( model.getDependencies().size(), equalTo( 1 ) );

        final Dependency dep = model.getDependencies().get( 0 );

        assertThat( dep.getGroupId(), equalTo( "new.group.id" ) );
        assertThat( dep.getArtifactId(), equalTo( "new-artifact" ) );
        assertThat( dep.getVersion(), equalTo( "1.0.0" ) );

        System.out.println( "\n\n" );
    }
}
