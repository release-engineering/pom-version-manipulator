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
import static com.redhat.rcm.version.testutil.TestProjectUtils.loadProjectKey;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.codehaus.plexus.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.redhat.rcm.version.fixture.LoggingFixture;

public class BOMManagementTest
    extends AbstractVersionManagerTest
{

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
    public void modifySinglePom_NormalizeToBOMUsage()
        throws Exception
    {
        System.out.println( "Single POM test (normalize to BOM usage)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bom = getResourceFile( "bom.xml" );

        final File pom = new File( repo, srcPom.getName() );
        FileUtils.copyFile( srcPom, pom );

        final VersionManagerSession session = new VersionManagerSession( workspace, reports, null, false );

        final Set<File> modified =
            vman.modifyVersions( pom, Collections.singletonList( bom.getAbsolutePath() ), null, null, session );
        assertNoErrors( session );
        assertNormalizedToBOMs( modified, Collections.singleton( bom ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyCompleteRepository_NormalizeToBOMUsage()
        throws Exception
    {
        System.out.println( "Repository test (normalize to BOM usage)..." );

        final File srcRepo = getResourceFile( "project-dir" );
        final File bom = getResourceFile( "bom.xml" );

        FileUtils.copyDirectoryStructure( srcRepo, repo );

        final VersionManagerSession session = new VersionManagerSession( workspace, reports, null, false );

        final Set<File> modified =
            vman.modifyVersions( repo,
                                 "pom.xml",
                                 Collections.singletonList( bom.getAbsolutePath() ),
                                 null,
                                 null,
                                 session );
        assertNoErrors( session );
        assertNormalizedToBOMs( modified, Collections.singleton( bom ) );

        System.out.println( "\n\n" );
    }

    private void assertNormalizedToBOMs( final Set<File> modified, final Set<File> boms )
        throws Exception
    {
        assertNotNull( modified );

        Set<FullProjectKey> bomKeys = new HashSet<FullProjectKey>();
        for ( File bom : boms )
        {
            bomKeys.add( loadProjectKey( bom ) );
        }

        for ( final File out : modified )
        {
            System.out.println( "Examining: " + out );

            Model model = loadModel( out );

            // NOTE: Assuming injection of BOMs will happen in toolchain ancestor now...
            //
            // final DependencyManagement dm = model.getDependencyManagement();
            // if ( dm != null )
            // {
            // Set<FullProjectKey> foundBoms = new HashSet<FullProjectKey>();
            //
            // for ( final Dependency dep : dm.getDependencies() )
            // {
            // if ( ( "pom".equals( dep.getType() ) && Artifact.SCOPE_IMPORT.equals( dep.getScope() ) ) )
            // {
            // foundBoms.add( new FullProjectKey( dep ) );
            // }
            // else
            // {
            // assertNull( "Managed Dependency version was NOT nullified: " + dep.getManagementKey()
            // + "\nPOM: " + out, dep.getVersion() );
            // }
            // }
            //
            // assertThat( foundBoms, equalTo( bomKeys ) );
            // }

            for ( final Dependency dep : model.getDependencies() )
            {
                if ( !( "pom".equals( dep.getType() ) && Artifact.SCOPE_IMPORT.equals( dep.getScope() ) ) )
                {
                    assertNull( "Dependency version was NOT nullified: " + dep.getManagementKey() + "\nPOM: " + out,
                                dep.getVersion() );
                }
            }
        }
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

        final VersionManagerSession session = newVersionManagerSession();

        final Set<File> results =
            vman.modifyVersions( repo, "**/*.pom", Collections.singletonList( bom ), null, null, session );
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

        final VersionManagerSession session = newVersionManagerSession();

        /* final File out = */vman.modifyVersions( pom,
                                                   Collections.singletonList( bom ),
                                                   getToolchainPath(),
                                                   null,
                                                   session );
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

        final VersionManagerSession session = newVersionManagerSession();

        vman.modifyVersions( pom, Collections.singletonList( bom ), null, null, session );
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

        final VersionManagerSession session = newVersionManagerSession();

        final Set<File> modified = vman.modifyVersions( pom, Collections.singletonList( bom ), null, null, session );
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

}
