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

import static com.redhat.rcm.version.testutil.TestProjectFixture.getResourceFile;
import static com.redhat.rcm.version.testutil.TestProjectFixture.loadModel;
import static com.redhat.rcm.version.testutil.TestProjectFixture.newVersionManagerSession;
import static com.redhat.rcm.version.testutil.VManAssertions.assertModelsNormalizedToBOMs;
import static com.redhat.rcm.version.testutil.VManAssertions.assertNoErrors;
import static com.redhat.rcm.version.testutil.VManAssertions.assertProjectNormalizedToBOMs;
import static com.redhat.rcm.version.testutil.VManAssertions.assertProjectsNormalizedToBOMs;
import static org.apache.commons.lang.StringUtils.join;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.junit.Rule;
import org.junit.Test;

import com.redhat.rcm.version.mgr.capture.MissingInfoCapture;
import com.redhat.rcm.version.mgr.session.SessionBuilder;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.testutil.TestProjectFixture;

public class BomModderTest
    extends AbstractModderTest
{

    private static final String TEST_DIR = "relocations/";

    @Rule
    public TestProjectFixture fixture = new TestProjectFixture();

    @Test
    public void modifyProjectTree_BOMInjected()
        throws Exception
    {
        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final String base = "bom-injection-multi/";

        final File parentFile = getResourceFile( base + "pom.xml" );
        final File childFile = getResourceFile( base + "child/pom.xml" );
        final File bomFile = getResourceFile( base + "bom.xml" );

        fixture.getVman()
               .configureSession( Collections.singletonList( bomFile.getAbsolutePath() ), null, session, parentFile,
                                  childFile, bomFile );

        final Project parent = fixture.loadProject( parentFile, session );
        final Project child = fixture.loadProject( childFile, session );

        final Project bom = fixture.loadProject( bomFile, session );

        final Set<Project> projects = new HashSet<Project>();
        projects.add( parent );
        projects.add( child );

        session.setCurrentProjects( projects );

        final BomModder modder = new BomModder();

        final boolean[] changed = { modder.inject( child, session ), modder.inject( parent, session ) };

        assertNoErrors( session );

        // NOTE: Child POM not modified...nothing to do there!
        assertThat( changed[0], equalTo( false ) );
        assertThat( changed[1], equalTo( true ) );

        assertModelsNormalizedToBOMs( projects, Collections.singleton( bom ) );
    }

    @Test
    public void modifySinglePom_BOMInjected()
        throws Exception
    {
        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final String base = "bom-injection-single/";

        final File pomFile = getResourceFile( base + "pom.xml" );
        final File bom = getResourceFile( base + "bom.xml" );

        fixture.getVman()
               .configureSession( Collections.singletonList( bom.getAbsolutePath() ), null, session, pomFile, bom );

        final Project src = fixture.loadProject( pomFile, session );

        final boolean changed = new BomModder().inject( src, session );

        assertNoErrors( session );
        assertThat( changed, equalTo( true ) );
        assertProjectNormalizedToBOMs( src, Collections.singleton( fixture.loadProject( bom, session ) ) );
    }

    @Test
    public void modifySinglePom_BOMWithParentInRepo()
        throws Exception
    {
        final File srcDir = getResourceFile( "bom-parent-in-repo" );
        final File remoteRepo = new File( srcDir, "repo" );

        final Repository resolve = new Repository();

        resolve.setId( "vman" );
        resolve.setUrl( remoteRepo.toURI()
                                  .normalize()
                                  .toURL()
                                  .toExternalForm() );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        session.setResolveRepositories( resolve );

        final File bom = new File( srcDir, "bom.xml" );

        fixture.getVman()
               .configureSession( Collections.singletonList( bom.getAbsolutePath() ), null, session );

        final Project pom = fixture.loadProject( new File( srcDir, "project/pom.xml" ), session );

        final boolean changed = new BomModder().inject( pom, session );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );
        assertProjectNormalizedToBOMs( pom, Collections.singleton( fixture.loadProject( bom, session ) ) );
    }

    @Test
    public void modifySinglePom_BOMofBOMs()
        throws Exception
    {
        final File srcDir = getResourceFile( "bom-of-boms" );
        final File remoteRepo = new File( srcDir, "repo" );

        final Repository resolve = new Repository();

        resolve.setId( "vman" );
        resolve.setUrl( remoteRepo.toURI()
                                  .normalize()
                                  .toURL()
                                  .toExternalForm() );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        session.setResolveRepositories( resolve );

        final File bom = new File( srcDir, "bom.xml" );

        fixture.getVman()
               .configureSession( Collections.singletonList( bom.getAbsolutePath() ), null, session );

        final Project pom = fixture.loadProject( new File( srcDir, "project/pom.xml" ), session );

        final boolean changed = new BomModder().inject( pom, session );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );
        assertProjectNormalizedToBOMs( pom, Collections.singleton( fixture.loadProject( bom, session ) ) );

    }

    @Test
    public void modifySinglePom_NormalizeToBOMUsage()
        throws Exception
    {
        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final File pomFile = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bomFile = getResourceFile( "bom.xml" );

        fixture.getVman()
               .configureSession( Collections.singletonList( bomFile.getAbsolutePath() ), null, session, pomFile,
                                  bomFile );

        final Project pom = fixture.loadProject( pomFile, session );
        final Project bom = fixture.loadProject( bomFile, session );

        final boolean changed = new BomModder().inject( pom, session );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );
        assertProjectNormalizedToBOMs( pom, Collections.singleton( bom ) );

    }

    @Test
    public void modifyMultimodule_NormalizeToBOMUsage()
        throws Exception
    {

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final Set<Project> poms = fixture.loadProjects( "project-dir", session );

        final File bomFile = getResourceFile( "bom.xml" );
        final Project bom = fixture.loadProject( bomFile, session );

        fixture.getVman()
               .configureSession( Collections.singletonList( bomFile.getAbsolutePath() ), null, session );

        session.setCurrentProjects( poms );

        final Map<Project, Boolean> results = new HashMap<Project, Boolean>();
        final BomModder modder = new BomModder();
        for ( final Project pom : poms )
        {
            results.put( pom, modder.inject( pom, session ) );
        }

        assertNoErrors( session );

        assertProjectsNormalizedToBOMs( poms, Collections.singleton( bom ) );

    }

    @Test
    public void modifyMultimodule_IgnoreProjectInterdependency()
        throws Exception
    {

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final Set<Project> poms = fixture.loadProjects( "project-dir-with-interdep", session );
        final Set<VersionlessProjectKey> skipped = new HashSet<VersionlessProjectKey>( poms.size() );
        for ( final Project project : poms )
        {
            skipped.add( new VersionlessProjectKey( project.getKey() ) );
        }

        final File bomFile = getResourceFile( "bom.xml" );

        final Project bom = fixture.loadProject( bomFile, session );

        fixture.getVman()
               .configureSession( Collections.singletonList( bomFile.getAbsolutePath() ), null, session );

        session.setCurrentProjects( poms );

        final Map<Project, Boolean> results = new HashMap<Project, Boolean>();
        final BomModder modder = new BomModder();
        for ( final Project pom : poms )
        {
            results.put( pom, modder.inject( pom, session ) );
        }

        assertNoErrors( session );

        final Set<Dependency> result =
            assertProjectsNormalizedToBOMs( poms, Collections.singleton( bom ),
                                            skipped.toArray( new VersionlessProjectKey[] {} ) );

        for ( final Dependency dep : result )
        {
            assertThat( "Dependency: " + dep + " should NOT be modified!", dep.getVersion(), notNullValue() );
        }

    }

    @Test
    public void modifySinglePomUsingInterpolatedBOM()
        throws Exception
    {
        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final File pomFile = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bomFile = getResourceFile( "bom.interp.xml" );

        fixture.getVman()
               .configureSession( Collections.singletonList( bomFile.getAbsolutePath() ), null, session, pomFile,
                                  bomFile );

        final Project pom = fixture.loadProject( pomFile, session );

        final boolean changed = new BomModder().inject( pom, session );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );
    }

    @Test
    public void modifySinglePomWithRelocations()
        throws Exception
    {
        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final File pomFile = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bomFile = getResourceFile( "bom-relocations.xml" );

        fixture.getVman()
               .configureSession( Collections.singletonList( bomFile.getAbsolutePath() ), null, session, pomFile,
                                  bomFile );

        final Project pom = fixture.loadProject( pomFile, session );

        final boolean changed = new BomModder().inject( pom, session );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );

        final StringWriter sw = new StringWriter();
        new MavenXpp3Writer().write( sw, pom.getModel() );
        final String result = sw.toString();

        assertFalse( result.contains( "<groupId>commons-codec</groupId>" ) );
        assertFalse( result.contains( "<groupId>commons-lang</groupId>" ) );

    }

    @Test
    public void modifySinglePomWithRelocations_InBom()
        throws Exception
    {
        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "org.test:old-dep:1.0",
                                                                               "org.test:new-dep:1.1" )
                                                    .withStrict( true )
                                                    .build();

        final String bomPath = "bom-dep-1.0.pom";

        final File pomFile = getResourceFile( TEST_DIR + "relocate-dep.pom" );
        final File bomFile = getResourceFile( TEST_DIR + bomPath );

        fixture.getVman()
               .configureSession( Collections.singletonList( bomFile.getAbsolutePath() ), null, session, pomFile,
                                  bomFile );

        final Project project = fixture.loadProject( pomFile, session );

        final Model model = project.getModel();
        assertThat( model.getDependencies(), notNullValue() );
        assertThat( model.getDependencies()
                         .size(), equalTo( 1 ) );

        Dependency dep = model.getDependencies()
                              .get( 0 );

        assertThat( dep.getArtifactId(), equalTo( "old-dep" ) );
        assertThat( dep.getVersion(), equalTo( "1.0" ) );

        final boolean changed = new BomModder().inject( project, session );
        assertThat( changed, equalTo( true ) );

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
        throws Exception
    {
        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "org.test:old-dep:1.0",
                                                                               "org.test:new-dep:1.1" )
                                                    .withStrict( false )
                                                    .build();

        final String bomPath = "bom-empty-1.0.pom";
        final File pomFile = getResourceFile( TEST_DIR + "relocate-dep.pom" );
        final File bomFile = getResourceFile( TEST_DIR + bomPath );

        fixture.getVman()
               .configureSession( Collections.singletonList( bomFile.getAbsolutePath() ), null, session, pomFile,
                                  bomFile );

        final Project project = fixture.loadProject( pomFile, session );

        final Model model = project.getModel();
        assertThat( model.getDependencies(), notNullValue() );
        assertThat( model.getDependencies()
                         .size(), equalTo( 1 ) );

        Dependency dep = model.getDependencies()
                              .get( 0 );
        assertThat( dep.getArtifactId(), equalTo( "old-dep" ) );
        assertThat( dep.getVersion(), equalTo( "1.0" ) );

        final boolean changed = new BomModder().inject( project, session );
        assertThat( changed, equalTo( true ) );

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
        throws Exception
    {
        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "org.test:old-dep:1.0",
                                                                               "org.test:new-dep:1.1" )
                                                    .withStrict( true )
                                                    .build();

        final String bomPath = "bom-empty-1.0.pom";
        final File pomFile = getResourceFile( TEST_DIR + "relocate-dep.pom" );
        final File bomFile = getResourceFile( TEST_DIR + bomPath );

        fixture.getVman()
               .configureSession( Collections.singletonList( bomFile.getAbsolutePath() ), null, session, pomFile,
                                  bomFile );

        final Project project = fixture.loadProject( pomFile, session );

        final Model model = project.getModel();
        assertThat( model.getDependencies(), notNullValue() );
        assertThat( model.getDependencies()
                         .size(), equalTo( 1 ) );

        Dependency dep = model.getDependencies()
                              .get( 0 );

        assertThat( dep.getArtifactId(), equalTo( "old-dep" ) );
        assertThat( dep.getVersion(), equalTo( "1.0" ) );

        final boolean changed = new BomModder().inject( project, session );
        assertThat( changed, equalTo( true ) );

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
        throws Exception
    {

        final File pom = getResourceFile( "pom-with-relocation.xml" );
        final String bom = getResourceFile( "bom-min.xml" ).getAbsolutePath();

        final Model model = loadModel( pom );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "old.group.id:some-artifact:1",
                                                                               "new.group.id:new-artifact:1.0.0" )
                                                    .build();

        fixture.getVman()
               .configureSession( Collections.singletonList( bom ), null, session );

        new BomModder().inject( new Project( model ), session );

        assertNoErrors( session );

        assertThat( model.getDependencies()
                         .size(), equalTo( 1 ) );

        final Dependency dep = model.getDependencies()
                                    .get( 0 );

        assertThat( dep.getGroupId(), equalTo( "new.group.id" ) );
        assertThat( dep.getArtifactId(), equalTo( "new-artifact" ) );
        assertThat( dep.getVersion(), nullValue() );

    }

    @Test
    public void modifySinglePomWithNonBOMRelocatedCoordinatesWhenDepNotInBOM()
        throws Exception
    {

        final File pom = getResourceFile( "pom-with-relocation.xml" );
        final String bom = getResourceFile( "bom-empty.xml" ).getAbsolutePath();

        final Model model = loadModel( pom );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "old.group.id:some-artifact:1",
                                                                               "new.group.id:new-artifact:1.0.0" )
                                                    .build();

        fixture.getVman()
               .configureSession( Collections.singletonList( bom ), null, session );

        new BomModder().inject( new Project( model ), session );

        assertNoErrors( session );

        assertThat( model.getDependencies()
                         .size(), equalTo( 1 ) );

        final Dependency dep = model.getDependencies()
                                    .get( 0 );

        assertThat( dep.getGroupId(), equalTo( "new.group.id" ) );
        assertThat( dep.getArtifactId(), equalTo( "new-artifact" ) );
        assertThat( dep.getVersion(), equalTo( "1.0.0" ) );

    }

    @Test
    public void managedDepsMissingFromBOMIncludedInCapturePOM()
        throws Exception
    {

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

        final Model capture = loadModel( capturePom );

        assertThat( capture.getDependencyManagement(), notNullValue() );
        assertThat( capture.getDependencyManagement()
                           .getDependencies(), notNullValue() );

        System.out.println( join( capture.getDependencyManagement()
                                         .getDependencies(), "\n" ) );

        assertThat( capture.getDependencyManagement()
                           .getDependencies()
                           .size(), equalTo( 2 ) );

        assertProject( capture, project );

        final Dependency dep = capture.getDependencyManagement()
                                      .getDependencies()
                                      .get( 1 );

        assertThat( dep.getGroupId(), equalTo( "group.id" ) );
        assertThat( dep.getArtifactId(), equalTo( "some-artifact" ) );
        assertThat( dep.getVersion(), equalTo( "1" ) );

    }

    @Test
    public void managedDepsMissingFromBOMIncludedInCapturePOM_NonStrictMode()
        throws Exception
    {

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

        final Model capture = loadModel( capturePom );

        assertThat( capture.getDependencyManagement(), notNullValue() );
        assertThat( capture.getDependencyManagement()
                           .getDependencies(), notNullValue() );
        assertThat( capture.getDependencyManagement()
                           .getDependencies()
                           .size(), equalTo( 2 ) );

        assertProject( capture, project );

        final Dependency dep = capture.getDependencyManagement()
                                      .getDependencies()
                                      .get( 1 );

        assertThat( dep.getGroupId(), equalTo( "group.id" ) );
        assertThat( dep.getArtifactId(), equalTo( "some-artifact" ) );
        assertThat( dep.getVersion(), equalTo( "1" ) );

    }

    @Test
    public void injectBOMsAheadOfPreexistingBOMInStrictMode()
        throws Exception
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

        final boolean changed = new BomModder().inject( new Project( model ), session );

        assertNoErrors( session );
        assertThat( changed, equalTo( true ) );

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

    @Test
    public void managedDepWithNonStandardType_RemoveVersion_StrictMode()
        throws Exception
    {

        final File pom = getResourceFile( "pom-with-custom-managed-dep.xml" );
        final File bom = getResourceFile( "bom-typed-min.xml" );

        final VersionManagerSession session = new SessionBuilder( workspace, reports ).build();

        fixture.getVman()
               .configureSession( Collections.singletonList( bom.getAbsolutePath() ), null, session, pom, bom );

        final Project project = fixture.loadProject( pom, session );
        final Project bomProject = fixture.loadProject( bom, session );

        final Set<Project> projects = new HashSet<Project>();
        projects.add( project );

        session.setCurrentProjects( projects );

        final boolean changed = new BomModder().inject( project, session );

        assertThat( changed, equalTo( true ) );

        assertNoErrors( session );

        final Model model = project.getModel();

        assertThat( model.getDependencyManagement(), notNullValue() );
        assertThat( model.getDependencyManagement()
                         .getDependencies(), notNullValue() );
        assertThat( model.getDependencyManagement()
                         .getDependencies()
                         .size(), equalTo( 1 ) );

        assertBoms( model, bomProject.getKey() );
    }

    private void assertBoms( final Model model, final FullProjectKey... bomKeys )
    {
        final DependencyManagement dm = model.getDependencyManagement();
        assertThat( dm, notNullValue() );

        final List<Dependency> deps = dm.getDependencies();
        assertThat( deps, notNullValue() );
        assertThat( deps.size() >= bomKeys.length, equalTo( true ) );

        for ( int i = 0; i < bomKeys.length; i++ )
        {
            final FullProjectKey bom = bomKeys[i];
            final Dependency dep = deps.get( i );

            final String exp = "BOM: " + bom + ", dep: " + dep;
            assertThat( exp, dep.getGroupId(), equalTo( bom.getGroupId() ) );
            assertThat( exp, dep.getArtifactId(), equalTo( bom.getArtifactId() ) );
            assertThat( exp, dep.getVersion(), equalTo( bom.getVersion() ) );
            assertThat( exp, dep.getType(), equalTo( "pom" ) );
            assertThat( exp, dep.getScope(), equalTo( "import" ) );
        }
    }

    private void assertProject( final Model model, final Project project )
    {
        final DependencyManagement dm = model.getDependencyManagement();
        assertThat( dm, notNullValue() );

        final List<Dependency> deps = dm.getDependencies();
        assertThat( deps, notNullValue() );
        assertThat( deps.size() > 0, equalTo( true ) );

        final Dependency dep = deps.get( 0 );
        final String exp = "My Project: " + project.getModel() + ", dep: " + dep;
        assertThat( exp, dep.getGroupId(), equalTo( project.getGroupId() ) );
        assertThat( exp, dep.getArtifactId(), equalTo( project.getArtifactId() ) );
        assertThat( exp, dep.getVersion(), equalTo( project.getVersion() ) );
        assertThat( exp, dep.getType(), equalTo( project.getModel()
                                                        .getPackaging() ) );
    }

    @Test
    public void managedDepWithDifferentTypeFromBOMDep_PreserveVersion_StrictMode()
        throws Exception
    {

        final File pom = getResourceFile( "pom-with-typed-managed-dep.xml" );
        final File bom = getResourceFile( "bom-typed-min.xml" );

        final VersionManagerSession session = new SessionBuilder( workspace, reports ).build();

        fixture.getVman()
               .configureSession( Collections.singletonList( bom.getAbsolutePath() ), null, session, pom, bom );

        final Project project = fixture.loadProject( pom, session );
        final Project bomProject = fixture.loadProject( bom, session );

        final Set<Project> projects = new HashSet<Project>();
        projects.add( project );

        session.setCurrentProjects( projects );

        new BomModder().inject( project, session );

        assertNoErrors( session );

        final Model model = project.getModel();

        assertThat( model.getDependencyManagement(), notNullValue() );

        assertThat( model.getDependencyManagement()
                         .getDependencies(), notNullValue() );

        assertThat( model.getDependencyManagement()
                         .getDependencies()
                         .size(), equalTo( 2 ) );

        assertBoms( model, bomProject.getKey() );

        final Dependency dep = model.getDependencyManagement()
                                    .getDependencies()
                                    .get( 1 );

        assertThat( dep.getGroupId(), equalTo( "group.id" ) );
        assertThat( dep.getArtifactId(), equalTo( "some-artifact" ) );
        assertThat( dep.getVersion(), equalTo( "1" ) );
        assertThat( dep.getType(), equalTo( "test-jar" ) );

    }

    @Test
    public void managedDepInProfile_StrictMode()
        throws Exception
    {

        final File pom = getResourceFile( "pom-with-profile-managed-dep.xml" );
        final File bom = getResourceFile( "bom-typed-min.xml" );

        final VersionManagerSession session = new SessionBuilder( workspace, reports ).build();

        fixture.getVman()
               .configureSession( Collections.singletonList( bom.getAbsolutePath() ), null, session, pom, bom );

        final Project project = fixture.loadProject( pom, session );

        Model model = project.getModel();

        assertThat( model.getProfiles(), notNullValue() );
        assertThat( model.getProfiles()
                         .size(), equalTo( 1 ) );

        Profile p = model.getProfiles()
                         .get( 0 );
        assertThat( p.getDependencyManagement(), notNullValue() );

        assertThat( p.getDependencyManagement()
                     .getDependencies(), notNullValue() );

        assertThat( p.getDependencyManagement()
                     .getDependencies()
                     .size(), equalTo( 1 ) );

        final Dependency dep = p.getDependencyManagement()
                                .getDependencies()
                                .get( 0 );

        assertThat( dep.getGroupId(), equalTo( "group.id" ) );
        assertThat( dep.getArtifactId(), equalTo( "some-artifact" ) );
        assertThat( dep.getVersion(), equalTo( "1" ) );

        final Project bomProject = fixture.loadProject( bom, session );

        final Set<Project> projects = new HashSet<Project>();
        projects.add( project );

        session.setCurrentProjects( projects );

        new BomModder().inject( project, session );

        assertNoErrors( session );

        model = project.getModel();

        assertBoms( model, bomProject.getKey() );

        assertThat( model.getProfiles(), notNullValue() );
        assertThat( model.getProfiles()
                         .size(), equalTo( 1 ) );

        p = model.getProfiles()
                 .get( 0 );
        assertThat( p.getDependencyManagement() == null || p.getDependencyManagement()
                                                            .getDependencies() == null || p.getDependencyManagement()
                                                                                           .getDependencies()
                                                                                           .isEmpty(), equalTo( true ) );

    }

}
