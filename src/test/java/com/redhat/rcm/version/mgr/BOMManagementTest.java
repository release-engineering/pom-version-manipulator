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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.ReaderFactory;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Set;

public class BOMManagementTest extends AbstractVersionManagerTest
{
    
    @Test
    public void modifySinglePom_NormalizeToBOMUsage()
        throws Exception
    {
        System.setProperty( "debug", Boolean.toString( true ) );
        System.out.println( "Single POM test (normalize to BOM usage)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final String bom = getResourceFile( "bom.xml" ).getAbsolutePath();

        final File pom = new File( repo, srcPom.getName() );
        FileUtils.copyFile( srcPom, pom );

        final VersionManagerSession session = new VersionManagerSession( workspace, reports, false, true, false );

        final Set<File> modified = vman.modifyVersions( pom, Collections.singletonList( bom ), null, session );
        assertNoErrors( session );
        assertNormalizedToBOMs( modified );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyCompleteRepository_NormalizeToBOMUsage()
        throws Exception
    {
        System.out.println( "Repository test (normalize to BOM usage)..." );

        final File srcRepo = getResourceFile( "project-dir" );
        final String bom = getResourceFile( "bom.xml" ).getAbsolutePath();

        FileUtils.copyDirectoryStructure( srcRepo, repo );

        final VersionManagerSession session = new VersionManagerSession( workspace, reports, false, true, false );

        final Set<File> modified = vman.modifyVersions( repo, "pom.xml", Collections.singletonList( bom ), null, session );
        assertNoErrors( session );
        assertNormalizedToBOMs( modified );

        System.out.println( "\n\n" );
    }

    private void assertNormalizedToBOMs( final Set<File> modified )
        throws Exception
    {
        assertNotNull( modified );

        for ( final File out : modified )
        {
            System.out.println( "Examining: " + out );

            final Reader reader = ReaderFactory.newPlatformReader( out );
            final Model model = new MavenXpp3Reader().read( reader );
            final DependencyManagement dm = model.getDependencyManagement();
            if ( dm != null )
            {
                for ( final Dependency dep : dm.getDependencies() )
                {
                    if ( !( "pom".equals( dep.getType() ) && Artifact.SCOPE_IMPORT.equals( dep.getScope() ) ) )
                    {
                        assertNull( "Managed Dependency version was NOT nullified: " + dep.getManagementKey()
                            + "\nPOM: " + out, dep.getVersion() );
                    }
                }
            }

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

        modifyRepo( bom );

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

        final Set<File> results = vman.modifyVersions( repo, "**/*.pom", Collections.singletonList( bom ), null, session );
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

        modifyRepo( bom );

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

        modifyRepo( bom1, bom2 );

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

        modifyRepo( bom1, bom2 );

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

        /* final File out = */vman.modifyVersions( pom, Collections.singletonList( bom ), null, session );
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
    public void modifySinglePomWithRelocations()
        throws IOException
    {
        System.out.println( "Single POM test (with relocations)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final String bom = getResourceFile( "bom-relocations.xml" ).getAbsolutePath();

        final File pom = new File( repo, srcPom.getName() );
        FileUtils.copyFile( srcPom, pom );

        final VersionManagerSession session = newVersionManagerSession();

        final Set<File> modified = vman.modifyVersions( pom, Collections.singletonList( bom ), null, session );
        assertNoErrors( session );
        
        assertNotNull( modified );
        assertEquals( 1, modified.size() );

        final File out = modified.iterator().next();
        vman.generateReports( reports, session );

        final String result = FileUtils.fileRead( out );
        assertTrue( "commons-codec not relocated!", result.contains( "<groupId>org.apache.commons.codec</groupId>" ) );

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

        vman.modifyVersions( pom, Collections.singletonList( bom ), null, session );
        assertNoErrors( session );
        vman.generateReports( reports, session );

        System.out.println( "\n\n" );
    }

}
