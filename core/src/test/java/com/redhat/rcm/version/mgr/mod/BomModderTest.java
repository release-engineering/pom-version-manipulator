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

import static com.redhat.rcm.version.testutil.TestProjectUtils.getResourceFile;
import static com.redhat.rcm.version.testutil.TestProjectUtils.loadModel;
import static com.redhat.rcm.version.testutil.TestProjectUtils.loadProject;
import static com.redhat.rcm.version.testutil.TestProjectUtils.loadProjects;
import static com.redhat.rcm.version.testutil.TestProjectUtils.newVersionManagerSession;
import static com.redhat.rcm.version.testutil.VManAssertions.assertModelsNormalizedToBOMs;
import static com.redhat.rcm.version.testutil.VManAssertions.assertNoErrors;
import static com.redhat.rcm.version.testutil.VManAssertions.assertProjectNormalizedToBOMs;
import static com.redhat.rcm.version.testutil.VManAssertions.assertProjectsNormalizedToBOMs;
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

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.junit.Test;

import com.redhat.rcm.version.mgr.capture.MissingInfoCapture;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.testutil.SessionBuilder;

public class BomModderTest
    extends AbstractModderTest
{

    private static final String TEST_DIR = "relocations/";

    @Test
    public void modifyProjectTree_BOMInjected()
        throws Exception
    {
        final String base = "bom-injection-multi/";
        final Model parent = loadModel( base + "pom.xml" );
        final Model child = loadModel( base + "child/pom.xml" );

        final List<Model> models = new ArrayList<Model>( 2 );
        models.add( child );
        models.add( parent );

        final Project bom = loadProject( base + "bom.xml" );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        session.setCurrentProjects( models );
        configureSession( Collections.singletonList( bom.getPom()
                                                        .getAbsolutePath() ), null, session );

        final BomModder modder = new BomModder();

        final boolean[] changed =
            { modder.inject( new Project( child ), session ), modder.inject( new Project( parent ), session ) };

        assertNoErrors( session );

        // NOTE: Child POM not modified...nothing to do there!
        assertThat( changed[0], equalTo( false ) );
        assertThat( changed[1], equalTo( true ) );

        assertModelsNormalizedToBOMs( models, Collections.singleton( bom ) );
    }

    @Test
    public void modifySinglePom_BOMInjected()
        throws Exception
    {
        final String base = "bom-injection-single/";
        final Project src = loadProject( base + "pom.xml" );
        final File bom = getResourceFile( base + "bom.xml" );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        configureSession( Collections.singletonList( bom.getAbsolutePath() ), null, session );

        final boolean changed = new BomModder().inject( src, session );

        assertNoErrors( session );
        assertThat( changed, equalTo( true ) );
        assertProjectNormalizedToBOMs( src, Collections.singleton( loadProject( bom ) ) );
    }

    @Test
    public void modifySinglePom_BOMWithParentInRepo()
        throws Exception
    {
        final File srcDir = getResourceFile( "bom-parent-in-repo" );
        final File remoteRepo = new File( srcDir, "repo" );

        final File bom = new File( srcDir, "bom.xml" );
        final Project pom = loadProject( new File( srcDir, "project/pom.xml" ) );

        final Repository resolve = new Repository();

        resolve.setId( "vman" );
        resolve.setUrl( remoteRepo.toURI()
                                  .normalize()
                                  .toURL()
                                  .toExternalForm() );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        session.setResolveRepositories( resolve );
        configureSession( Collections.singletonList( bom.getAbsolutePath() ), null, session );

        final boolean changed = new BomModder().inject( pom, session );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );
        assertProjectNormalizedToBOMs( pom, Collections.singleton( loadProject( bom ) ) );
    }

    @Test
    public void modifySinglePom_BOMofBOMs()
        throws Exception
    {
        System.out.println( "BOM-of-BOMS test (normalize to BOM usage)..." );

        final File srcDir = getResourceFile( "bom-of-boms" );
        final File remoteRepo = new File( srcDir, "repo" );

        final File bom = new File( srcDir, "bom.xml" );
        final Project pom = loadProject( new File( srcDir, "project/pom.xml" ) );

        final Repository resolve = new Repository();

        resolve.setId( "vman" );
        resolve.setUrl( remoteRepo.toURI()
                                  .normalize()
                                  .toURL()
                                  .toExternalForm() );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        session.setResolveRepositories( resolve );

        configureSession( Collections.singletonList( bom.getAbsolutePath() ), null, session );

        final boolean changed = new BomModder().inject( pom, session );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );
        assertProjectNormalizedToBOMs( pom, Collections.singleton( loadProject( bom ) ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePom_NormalizeToBOMUsage()
        throws Exception
    {
        System.out.println( "Single POM test (normalize to BOM usage)..." );

        final Project pom = loadProject( "rwx-parent-0.2.1.pom" );
        final Project bom = loadProject( "bom.xml" );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        configureSession( Collections.singletonList( bom.getPom()
                                                        .getAbsolutePath() ), null, session );

        final boolean changed = new BomModder().inject( pom, session );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );
        assertProjectNormalizedToBOMs( pom, Collections.singleton( bom ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyMultimodule_NormalizeToBOMUsage()
        throws Exception
    {
        System.out.println( "Mult-module project tree test (normalize to BOM usage)..." );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final Set<Project> poms = loadProjects( "project-dir", session );
        final Project bom = loadProject( "bom.xml" );

        configureSession( Collections.singletonList( bom.getPom()
                                                        .getAbsolutePath() ), null, session );
        session.setCurrentProjects( poms );

        final Map<Project, Boolean> results = new HashMap<Project, Boolean>();
        final BomModder modder = new BomModder();
        for ( final Project pom : poms )
        {
            results.put( pom, modder.inject( pom, session ) );
        }

        assertNoErrors( session );

        assertProjectsNormalizedToBOMs( poms, Collections.singleton( bom ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyMultimodule_IgnoreProjectInterdependency()
        throws Exception
    {
        System.out.println( "Multi-module tree with interdependencies test (normalize to BOM usage)..." );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final Set<Project> poms = loadProjects( "project-dir-with-interdep", session );
        final Set<VersionlessProjectKey> skipped = new HashSet<VersionlessProjectKey>( poms.size() );
        for ( final Project project : poms )
        {
            skipped.add( new VersionlessProjectKey( project.getKey() ) );
        }

        final Project bom = loadProject( "bom.xml" );

        configureSession( Collections.singletonList( bom.getPom()
                                                        .getAbsolutePath() ), null, session );
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

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyCompleteRepositoryVersions()
        throws Exception
    {
        System.out.println( "Complete repository test..." );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final Set<Project> poms = loadProjects( "repository", session );
        final Set<VersionlessProjectKey> skipped = new HashSet<VersionlessProjectKey>( poms.size() );
        for ( final Project project : poms )
        {
            skipped.add( new VersionlessProjectKey( project.getKey() ) );
        }

        final Project bom = loadProject( "bom.xml" );

        configureSession( Collections.singletonList( bom.getPom()
                                                        .getAbsolutePath() ), null, session );
        session.setCurrentProjects( poms );

        final Map<Project, Boolean> results = new HashMap<Project, Boolean>();
        final BomModder modder = new BomModder();
        for ( final Project pom : poms )
        {
            results.put( pom, modder.inject( pom, session ) );
        }

        assertNoErrors( session );
        assertProjectsNormalizedToBOMs( poms, Collections.singleton( bom ),
                                        skipped.toArray( new VersionlessProjectKey[] {} ) );
    }

    @Test
    public void modifyRepositoryVersionsWithoutChangingTheRest()
        throws Exception
    {
        System.out.println( "Repository POM non-interference test..." );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final Set<Project> poms = loadProjects( "projects-with-property-refs", session );
        final Set<VersionlessProjectKey> skipped = new HashSet<VersionlessProjectKey>( poms.size() );
        for ( final Project project : poms )
        {
            skipped.add( new VersionlessProjectKey( project.getKey() ) );
        }

        final Project bom = loadProject( "bom.xml" );

        configureSession( Collections.singletonList( bom.getPom()
                                                        .getAbsolutePath() ), null, session );
        session.setCurrentProjects( poms );

        final Map<Project, Boolean> results = new HashMap<Project, Boolean>();
        final BomModder modder = new BomModder();
        for ( final Project pom : poms )
        {
            results.put( pom, modder.inject( pom, session ) );
        }

        assertNoErrors( session );
        assertProjectsNormalizedToBOMs( poms, Collections.singleton( bom ),
                                        skipped.toArray( new VersionlessProjectKey[] {} ) );
        for ( final Project project : poms )
        {
            if ( "rwx-parent".equals( project.getArtifactId() ) )
            {
                assertThat( project.getModel()
                                   .getBuild(), notNullValue() );
                assertThat( project.getModel()
                                   .getBuild()
                                   .getFinalName(), equalTo( "${artifactId}" ) );

                break;
            }
        }
    }

    @Test
    public void modifyPartialRepositoryVersions()
        throws Exception
    {
        System.out.println( "Partial repository test..." );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final Set<Project> poms = loadProjects( "repository.partial", session );
        final Set<VersionlessProjectKey> skipped = new HashSet<VersionlessProjectKey>( poms.size() );
        for ( final Project project : poms )
        {
            skipped.add( new VersionlessProjectKey( project.getKey() ) );
        }

        final Project bom = loadProject( "bom.xml" );

        configureSession( Collections.singletonList( bom.getPom()
                                                        .getAbsolutePath() ), null, session );
        session.setCurrentProjects( poms );

        final Map<Project, Boolean> results = new HashMap<Project, Boolean>();
        final BomModder modder = new BomModder();
        for ( final Project pom : poms )
        {
            results.put( pom, modder.inject( pom, session ) );
        }

        assertNoErrors( session );

        assertProjectsNormalizedToBOMs( poms, Collections.singleton( bom ),
                                        skipped.toArray( new VersionlessProjectKey[] {} ) );
    }

    @Test
    public void modifyCompleteRepositoryVersions_UsingTwoBoms()
        throws Exception
    {
        System.out.println( "Complete repository test..." );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final Set<Project> poms = loadProjects( "repository", session );

        final Set<VersionlessProjectKey> skipped = new HashSet<VersionlessProjectKey>( poms.size() );
        for ( final Project project : poms )
        {
            skipped.add( new VersionlessProjectKey( project.getKey() ) );
        }

        final Set<Project> boms = new HashSet<Project>();
        boms.add( loadProject( "bom.part1.xml" ) );
        boms.add( loadProject( "bom.part2.xml" ) );

        final List<String> bomPaths = new ArrayList<String>();
        for ( final Project bom : boms )
        {
            bomPaths.add( bom.getPom()
                             .getAbsolutePath() );
        }

        configureSession( bomPaths, null, session );

        session.setCurrentProjects( poms );

        final Map<Project, Boolean> results = new HashMap<Project, Boolean>();
        final BomModder modder = new BomModder();
        for ( final Project pom : poms )
        {
            results.put( pom, modder.inject( pom, session ) );
        }

        assertNoErrors( session );

        assertProjectsNormalizedToBOMs( poms, boms, skipped.toArray( new VersionlessProjectKey[] {} ) );
    }

    @Test
    public void modifyPartialRepositoryVersions_UsingTwoBoms()
        throws Exception
    {
        System.out.println( "Partial repository test..." );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );

        final Set<Project> poms = loadProjects( "repository.partial", session );
        final Set<VersionlessProjectKey> skipped = new HashSet<VersionlessProjectKey>( poms.size() );
        for ( final Project project : poms )
        {
            skipped.add( new VersionlessProjectKey( project.getKey() ) );
        }

        final Set<Project> boms = new HashSet<Project>();
        boms.add( loadProject( "bom.part1.xml" ) );
        boms.add( loadProject( "bom.part2.xml" ) );

        final List<String> bomPaths = new ArrayList<String>();
        for ( final Project bom : boms )
        {
            bomPaths.add( bom.getPom()
                             .getAbsolutePath() );
        }

        configureSession( bomPaths, null, session );

        session.setCurrentProjects( poms );

        final Map<Project, Boolean> results = new HashMap<Project, Boolean>();
        final BomModder modder = new BomModder();
        for ( final Project pom : poms )
        {
            results.put( pom, modder.inject( pom, session ) );
        }

        assertNoErrors( session );

        assertProjectsNormalizedToBOMs( poms, boms, skipped.toArray( new VersionlessProjectKey[] {} ) );
    }

    @Test
    public void modifySinglePomUsingInterpolatedBOM()
        throws Exception
    {
        System.out.println( "Single POM test (interpolated BOM)..." );

        final Project pom = loadProject( "rwx-parent-0.2.1.pom" );
        final Project bom = loadProject( "bom.interp.xml" );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        configureSession( Collections.singletonList( bom.getPom()
                                                        .getAbsolutePath() ), null, session );

        final boolean changed = new BomModder().inject( pom, session );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );
    }

    @Test
    public void modifySinglePomWithRelocations()
        throws Exception
    {
        System.out.println( "Single POM test (with relocations)..." );

        final Project pom = loadProject( "rwx-parent-0.2.1.pom" );
        final Project bom = loadProject( "bom-relocations.xml" );

        final VersionManagerSession session = newVersionManagerSession( workspace, reports, null );
        configureSession( Collections.singletonList( bom.getPom()
                                                        .getAbsolutePath() ), null, session );

        final boolean changed = new BomModder().inject( pom, session );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );

        final StringWriter sw = new StringWriter();
        new MavenXpp3Writer().write( sw, pom.getModel() );
        final String result = sw.toString();

        assertFalse( result.contains( "<groupId>commons-codec</groupId>" ) );
        assertFalse( result.contains( "<groupId>commons-lang</groupId>" ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePomWithRelocations_InBom()
        throws Exception
    {
        final Project project = loadProject( TEST_DIR + "relocate-dep.pom" );

        final String bomPath = "bom-dep-1.0.pom";
        final Project bom = loadProject( TEST_DIR + bomPath );

        final Model model = project.getModel();
        assertThat( model.getDependencies(), notNullValue() );
        assertThat( model.getDependencies()
                         .size(), equalTo( 1 ) );

        Dependency dep = model.getDependencies()
                              .get( 0 );

        assertThat( dep.getArtifactId(), equalTo( "old-dep" ) );
        assertThat( dep.getVersion(), equalTo( "1.0" ) );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "org.test:old-dep:1.0",
                                                                               "org.test:new-dep:1.1" )
                                                    .withStrict( true )
                                                    .build();

        configureSession( Collections.singletonList( bom.getPom()
                                                        .getAbsolutePath() ), null, session );

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
        final Project project = loadProject( TEST_DIR + "relocate-dep.pom" );

        final String bomPath = "bom-empty-1.0.pom";
        final Project bom = loadProject( TEST_DIR + bomPath );

        final Model model = project.getModel();
        assertThat( model.getDependencies(), notNullValue() );
        assertThat( model.getDependencies()
                         .size(), equalTo( 1 ) );

        Dependency dep = model.getDependencies()
                              .get( 0 );
        assertThat( dep.getArtifactId(), equalTo( "old-dep" ) );
        assertThat( dep.getVersion(), equalTo( "1.0" ) );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "org.test:old-dep:1.0",
                                                                               "org.test:new-dep:1.1" )
                                                    .withStrict( false )
                                                    .build();

        configureSession( Collections.singletonList( bom.getPom()
                                                        .getAbsolutePath() ), null, session );

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
        final Project project = loadProject( TEST_DIR + "relocate-dep.pom" );

        final String bomPath = "bom-empty-1.0.pom";
        final Project bom = loadProject( TEST_DIR + bomPath );

        final Model model = project.getModel();
        assertThat( model.getDependencies(), notNullValue() );
        assertThat( model.getDependencies()
                         .size(), equalTo( 1 ) );

        Dependency dep = model.getDependencies()
                              .get( 0 );

        assertThat( dep.getArtifactId(), equalTo( "old-dep" ) );
        assertThat( dep.getVersion(), equalTo( "1.0" ) );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "org.test:old-dep:1.0",
                                                                               "org.test:new-dep:1.1" )
                                                    .withStrict( true )
                                                    .build();

        configureSession( Collections.singletonList( bom.getPom()
                                                        .getAbsolutePath() ), null, session );

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
        System.out.println( "Single POM test (with relocations NOT from BOM)..." );

        final File pom = getResourceFile( "pom-with-relocation.xml" );
        final String bom = getResourceFile( "bom-min.xml" ).getAbsolutePath();

        final Model model = loadModel( pom );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "old.group.id:some-artifact:1",
                                                                               "new.group.id:new-artifact:1.0.0" )
                                                    .build();

        configureSession( Collections.singletonList( bom ), null, session );

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
        throws Exception
    {
        System.out.println( "Single POM test (with relocations NOT from BOM, no dep in BOM)..." );

        final File pom = getResourceFile( "pom-with-relocation.xml" );
        final String bom = getResourceFile( "bom-empty.xml" ).getAbsolutePath();

        final Model model = loadModel( pom );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "old.group.id:some-artifact:1",
                                                                               "new.group.id:new-artifact:1.0.0" )
                                                    .build();

        configureSession( Collections.singletonList( bom ), null, session );

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
        throws Exception
    {
        System.out.println( "capture missing managed deps..." );

        final File pom = getResourceFile( "pom-with-managed-dep.xml" );
        final String bom = getResourceFile( "bom-min.xml" ).getAbsolutePath();

        final Model model = loadModel( pom );

        final VersionManagerSession session = new SessionBuilder( workspace, reports ).build();

        final File capturePom = tempFolder.newFile( "capture.pom" );
        session.setCapturePom( capturePom );
        session.setCurrentProjects( Collections.singleton( model ) );

        configureSession( Collections.singletonList( bom ), null, session );

        new BomModder().inject( new Project( model ), session );
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
        throws Exception
    {
        System.out.println( "capture missing managed deps..." );

        final File pom = getResourceFile( "pom-with-managed-dep.xml" );
        final String bom = getResourceFile( "bom-min.xml" ).getAbsolutePath();

        final Model model = loadModel( pom );

        final VersionManagerSession session = new SessionBuilder( workspace, reports ).withStrict( false )
                                                                                      .build();

        final File capturePom = tempFolder.newFile( "capture.pom" );
        session.setCapturePom( capturePom );
        session.setCurrentProjects( Collections.singleton( model ) );

        configureSession( Collections.singletonList( bom ), null, session );

        new BomModder().inject( new Project( model ), session );
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
        throws Exception
    {
        final File pom = getResourceFile( "pom-with-existing-import.xml" );
        final String bom1 = getResourceFile( "bom-min.xml" ).getAbsolutePath();
        final String bom2 = getResourceFile( "bom-min2.xml" ).getAbsolutePath();

        final List<String> boms = new ArrayList<String>();
        boms.add( bom1 );
        boms.add( bom2 );

        final Model model = loadModel( pom );

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

        final VersionManagerSession session = new SessionBuilder( workspace, reports ).withStrict( true )
                                                                                      .build();

        session.setCurrentProjects( Collections.singleton( model ) );

        configureSession( boms, null, session );

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

}
