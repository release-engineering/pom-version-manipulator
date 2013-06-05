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

import static com.redhat.rcm.version.testutil.TestProjectFixture.getResourceFile;
import static com.redhat.rcm.version.testutil.TestProjectFixture.loadModel;
import static com.redhat.rcm.version.testutil.TestProjectFixture.newVersionManagerSession;
import static com.redhat.rcm.version.testutil.VManAssertions.assertPOMsNormalizedToBOMs;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.redhat.rcm.version.fixture.LoggingFixture;
import com.redhat.rcm.version.mgr.capture.MissingInfoCapture;
import com.redhat.rcm.version.mgr.mod.BomModder;
import com.redhat.rcm.version.mgr.session.SessionBuilder;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.testutil.TestProjectFixture;

public class BOMManagementTest
    extends AbstractVersionManagerTest
{

    private static final String TEST_DIR = "relocations/";

    @Rule
    public TestName name = new TestName();

    @Rule
    public TestProjectFixture fixture = new TestProjectFixture();

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
        assertPOMsNormalizedToBOMs( modified, Collections.singleton( bom ), session, fixture );

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
        assertPOMsNormalizedToBOMs( modified, Collections.singleton( bom ), session, fixture );

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
        resolve.setUrl( remoteRepo.toURI()
                                  .normalize()
                                  .toURL()
                                  .toExternalForm() );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        session.setResolveRepositories( resolve );

        final Set<File> modified =
            vman.modifyVersions( pom, Collections.singletonList( bom.getAbsolutePath() ), null, session );
        assertNoErrors( session );
        assertPOMsNormalizedToBOMs( modified, Collections.singleton( bom ), session, fixture );

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
        resolve.setUrl( remoteRepo.toURI()
                                  .normalize()
                                  .toURL()
                                  .toExternalForm() );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        session.setResolveRepositories( resolve );

        final Set<File> modified =
            vman.modifyVersions( pom, Collections.singletonList( bom.getAbsolutePath() ), null, session );
        assertNoErrors( session );
        assertPOMsNormalizedToBOMs( modified, Collections.singleton( bom ), session, fixture );

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
        assertPOMsNormalizedToBOMs( modified, Collections.singleton( bom ), session, fixture );

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

        assertPOMsNormalizedToBOMs( modified, Collections.singleton( bom ), session, fixture );

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
            assertPOMsNormalizedToBOMs( modified, Collections.singleton( bom ), session, fixture,
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
    public void modifySinglePom()
        throws Exception
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
        throws Exception
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
        throws Exception
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

        final File out = modified.iterator()
                                 .next();
        vman.generateReports( reports, session );

        final String result = FileUtils.fileRead( out );
        assertFalse( result.contains( "<groupId>commons-codec</groupId>" ) );
        assertFalse( result.contains( "<groupId>commons-lang</groupId>" ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePomWithRelocations_InBom()
        throws IOException, ProjectToolsException
    {
        final Model original = loadModel( TEST_DIR + "relocate-dep.pom" );

        final String bomPath = "bom-dep-1.0.pom";
        final Model bomModel = loadModel( TEST_DIR + bomPath );
        final MavenProject bomProject = new MavenProject( bomModel );
        bomProject.setOriginalModel( bomModel );

        assertThat( original.getDependencies(), notNullValue() );
        assertThat( original.getDependencies()
                            .size(), equalTo( 1 ) );

        Dependency dep = original.getDependencies()
                                 .get( 0 );
        assertThat( dep.getArtifactId(), equalTo( "old-dep" ) );
        assertThat( dep.getVersion(), equalTo( "1.0" ) );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "org.test:old-dep:1.0",
                                                                               "org.test:new-dep:1.1" )
                                                    .withStrict( true )
                                                    .build();

        session.addBOM( getResourceFile( TEST_DIR + bomPath ), bomProject );

        final Project project = new Project( original );

        final boolean changed = new BomModder().inject( project, session );
        assertThat( changed, equalTo( true ) );

        final Model model = project.getModel();
        assertThat( model.getDependencies(), notNullValue() );
        assertThat( model.getDependencies()
                         .size(), equalTo( 1 ) );

        dep = model.getDependencies()
                   .get( 0 );
        assertThat( dep.getArtifactId(), equalTo( "new-dep" ) );
        assertThat( dep.getVersion(), nullValue() );
    }

    @Test
    public void modifySinglePomWithRelocations_NotInBom_NonStrictMode()
        throws IOException, ProjectToolsException
    {
        final Model original = loadModel( TEST_DIR + "relocate-dep.pom" );

        final String bomPath = "bom-empty-1.0.pom";
        final Model bomModel = loadModel( TEST_DIR + bomPath );
        final MavenProject bomProject = new MavenProject( bomModel );
        bomProject.setOriginalModel( bomModel );

        assertThat( original.getDependencies(), notNullValue() );
        assertThat( original.getDependencies()
                            .size(), equalTo( 1 ) );

        Dependency dep = original.getDependencies()
                                 .get( 0 );
        assertThat( dep.getArtifactId(), equalTo( "old-dep" ) );
        assertThat( dep.getVersion(), equalTo( "1.0" ) );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "org.test:old-dep:1.0",
                                                                               "org.test:new-dep:1.1" )
                                                    .withStrict( false )
                                                    .build();

        session.addBOM( getResourceFile( TEST_DIR + bomPath ), bomProject );

        final Project project = new Project( original );

        final boolean changed = new BomModder().inject( project, session );
        assertThat( changed, equalTo( true ) );

        final Model model = project.getModel();
        assertThat( model.getDependencies(), notNullValue() );
        assertThat( model.getDependencies()
                         .size(), equalTo( 1 ) );

        dep = model.getDependencies()
                   .get( 0 );
        assertThat( dep.getArtifactId(), equalTo( "new-dep" ) );
        assertThat( dep.getVersion(), nullValue() );
    }

    @Test
    public void modifySinglePomWithRelocations_NotInBom_StrictMode()
        throws IOException, ProjectToolsException
    {
        final Model original = loadModel( TEST_DIR + "relocate-dep.pom" );

        final String bomPath = "bom-empty-1.0.pom";
        final Model bomModel = loadModel( TEST_DIR + bomPath );
        final MavenProject bomProject = new MavenProject( bomModel );
        bomProject.setOriginalModel( bomModel );

        assertThat( original.getDependencies(), notNullValue() );
        assertThat( original.getDependencies()
                            .size(), equalTo( 1 ) );

        Dependency dep = original.getDependencies()
                                 .get( 0 );
        assertThat( dep.getArtifactId(), equalTo( "old-dep" ) );
        assertThat( dep.getVersion(), equalTo( "1.0" ) );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "org.test:old-dep:1.0",
                                                                               "org.test:new-dep:1.1" )
                                                    .withStrict( true )
                                                    .build();

        session.addBOM( getResourceFile( TEST_DIR + bomPath ), bomProject );

        final Project project = new Project( original );

        final boolean changed = new BomModder().inject( project, session );
        assertThat( changed, equalTo( true ) );

        final Model model = project.getModel();
        assertThat( model.getDependencies(), notNullValue() );
        assertThat( model.getDependencies()
                         .size(), equalTo( 1 ) );

        dep = model.getDependencies()
                   .get( 0 );
        assertThat( dep.getArtifactId(), equalTo( "new-dep" ) );
        assertThat( dep.getVersion(), equalTo( "1.1" ) );
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

        assertThat( model.getDependencies()
                         .size(), equalTo( 1 ) );

        final Dependency dep = model.getDependencies()
                                    .get( 0 );

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

        assertThat( model.getDependencies()
                         .size(), equalTo( 1 ) );

        final Dependency dep = model.getDependencies()
                                    .get( 0 );

        assertThat( dep.getGroupId(), equalTo( "new.group.id" ) );
        assertThat( dep.getArtifactId(), equalTo( "new-artifact" ) );
        assertThat( dep.getVersion(), equalTo( "1.0.0" ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void managedDepsMissingFromBOMIncludedInCapturePOM()
        throws IOException, ProjectToolsException, ModelBuildingException
    {
        System.out.println( "capture missing managed deps..." );

        final File pom = getResourceFile( "pom-with-managed-dep.xml" );
        final File bom = getResourceFile( "bom-min.xml" );

        final VersionManagerSession session = new SessionBuilder( workspace, reports ).build();

        fixture.getVman()
               .configureSession( Collections.singletonList( bom.getAbsolutePath() ), null, session, pom, bom );

        final Project project = fixture.loadProject( pom, session );

        final Set<Project> projects = new HashSet<Project>();
        projects.add( project );

        session.setCurrentProjects( projects );

        final File capturePom = tempFolder.newFile( "capture.pom" );
        session.setCapturePom( capturePom );

        new BomModder().inject( project, session );
        new MissingInfoCapture().captureMissing( session );

        assertNoErrors( session );

        final Model capture = loadModel( pom );

        assertThat( capture.getDependencyManagement(), notNullValue() );
        assertThat( capture.getDependencyManagement()
                           .getDependencies(), notNullValue() );
        assertThat( capture.getDependencyManagement()
                           .getDependencies()
                           .size(), equalTo( 1 ) );

        final Dependency dep = capture.getDependencyManagement()
                                      .getDependencies()
                                      .get( 0 );

        assertThat( dep.getGroupId(), equalTo( "group.id" ) );
        assertThat( dep.getArtifactId(), equalTo( "some-artifact" ) );
        assertThat( dep.getVersion(), equalTo( "1" ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void managedDepsMissingFromBOMIncludedInCapturePOM_NonStrictMode()
        throws ProjectToolsException, ModelBuildingException, IOException
    {
        System.out.println( "capture missing managed deps..." );

        final File pom = getResourceFile( "pom-with-managed-dep.xml" );
        final File bom = getResourceFile( "bom-min.xml" );

        final VersionManagerSession session = new SessionBuilder( workspace, reports ).build();

        fixture.getVman()
               .configureSession( Collections.singletonList( bom.getAbsolutePath() ), null, session, pom, bom );

        final Project project = fixture.loadProject( pom, session );

        final Set<Project> projects = new HashSet<Project>();
        projects.add( project );

        session.setCurrentProjects( projects );

        final File capturePom = tempFolder.newFile( "capture.pom" );
        session.setCapturePom( capturePom );

        new BomModder().inject( project, session );
        new MissingInfoCapture().captureMissing( session );

        assertNoErrors( session );

        final Model capture = loadModel( pom );

        assertThat( capture.getDependencyManagement(), notNullValue() );
        assertThat( capture.getDependencyManagement()
                           .getDependencies(), notNullValue() );
        assertThat( capture.getDependencyManagement()
                           .getDependencies()
                           .size(), equalTo( 1 ) );

        final Dependency dep = capture.getDependencyManagement()
                                      .getDependencies()
                                      .get( 0 );

        assertThat( dep.getGroupId(), equalTo( "group.id" ) );
        assertThat( dep.getArtifactId(), equalTo( "some-artifact" ) );
        assertThat( dep.getVersion(), equalTo( "1" ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void injectBOMsAheadOfPreexistingBOMInStrictMode()
        throws ProjectToolsException, ModelBuildingException, IOException
    {
        final File pom = getResourceFile( "pom-with-existing-import.xml" );
        final File originalBom = getResourceFile( "some-bom.xml" );
        final File bom1 = getResourceFile( "bom-min.xml" );
        final File bom2 = getResourceFile( "bom-min2.xml" );

        final List<String> boms = new ArrayList<String>();
        boms.add( bom1.getAbsolutePath() );
        boms.add( bom2.getAbsolutePath() );

        final VersionManagerSession session = new SessionBuilder( workspace, reports ).build();

        fixture.getVman()
               .configureSession( boms, null, session, pom, bom1, bom2, originalBom );

        final Project project = fixture.loadProject( pom, session );
        final Model model = project.getModel();

        final Set<Project> projects = new HashSet<Project>();
        projects.add( project );

        session.setCurrentProjects( projects );

        assertThat( model.getDependencyManagement(), notNullValue() );
        assertThat( model.getDependencyManagement()
                         .getDependencies(), notNullValue() );
        assertThat( model.getDependencyManagement()
                         .getDependencies()
                         .size(), equalTo( 1 ) );

        Dependency dep = model.getDependencyManagement()
                              .getDependencies()
                              .get( 0 );

        assertThat( dep.getGroupId(), equalTo( "group.id" ) );
        assertThat( dep.getArtifactId(), equalTo( "some-bom" ) );
        assertThat( dep.getVersion(), equalTo( "1" ) );
        assertThat( dep.getType(), equalTo( "pom" ) );
        assertThat( dep.getScope(), equalTo( "import" ) );

        new BomModder().inject( project, session );

        assertNoErrors( session );

        assertThat( model.getDependencyManagement(), notNullValue() );
        assertThat( model.getDependencyManagement()
                         .getDependencies(), notNullValue() );
        assertThat( model.getDependencyManagement()
                         .getDependencies()
                         .size(), equalTo( 3 ) );

        int idx = 0;
        dep = model.getDependencyManagement()
                   .getDependencies()
                   .get( idx++ );

        assertThat( dep.getGroupId(), equalTo( "group" ) );
        assertThat( dep.getArtifactId(), equalTo( "bom-min" ) );
        assertThat( dep.getVersion(), equalTo( "1" ) );
        assertThat( dep.getType(), equalTo( "pom" ) );
        assertThat( dep.getScope(), equalTo( "import" ) );

        dep = model.getDependencyManagement()
                   .getDependencies()
                   .get( idx++ );

        assertThat( dep.getGroupId(), equalTo( "group" ) );
        assertThat( dep.getArtifactId(), equalTo( "bom-min2" ) );
        assertThat( dep.getVersion(), equalTo( "1" ) );
        assertThat( dep.getType(), equalTo( "pom" ) );
        assertThat( dep.getScope(), equalTo( "import" ) );

        dep = model.getDependencyManagement()
                   .getDependencies()
                   .get( idx++ );

        assertThat( dep.getGroupId(), equalTo( "group.id" ) );
        assertThat( dep.getArtifactId(), equalTo( "some-bom" ) );
        assertThat( dep.getVersion(), equalTo( "1" ) );
        assertThat( dep.getType(), equalTo( "pom" ) );
        assertThat( dep.getScope(), equalTo( "import" ) );
    }

}
