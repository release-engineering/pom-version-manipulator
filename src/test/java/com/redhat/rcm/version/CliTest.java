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

package com.redhat.rcm.version;

import static com.redhat.rcm.version.testutil.TestProjectUtils.loadModel;
import static com.redhat.rcm.version.testutil.VManAssertions.assertNormalizedToBOMs;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.io.FileUtils.writeLines;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.redhat.rcm.version.fixture.LoggingFixture;
import com.redhat.rcm.version.mgr.VersionManager;

public class CliTest
{

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    public void help()
        throws Exception
    {
        Cli.main( new String[] { "-h" } );
    }

    @Test
    public void modifyCompleteRepositoryVersions()
        throws Exception
    {
        System.out.println( "Complete repository test..." );

        final File srcRepo = getResourceFile( "repository" );
        final File bom = getResourceFile( "bom.xml" );

        copyDirectory( srcRepo, repo );

        modifyRepo( bom );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyPartialRepositoryVersions()
        throws Exception
    {
        System.out.println( "Partial repository test..." );

        final File srcRepo = getResourceFile( "repository.partial" );
        final File bom = getResourceFile( "bom.xml" );

        copyDirectory( srcRepo, repo );

        modifyRepo( bom );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyCompleteRepositoryVersions_UsingTwoBoms()
        throws Exception
    {
        System.out.println( "Complete repository test (two BOMs)..." );

        final File srcRepo = getResourceFile( "repository" );
        final File bom1 = getResourceFile( "bom.part1.xml" );
        final File bom2 = getResourceFile( "bom.part2.xml" );

        copyDirectory( srcRepo, repo );

        modifyRepo( bom1, bom2 );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyCompleteRepositoryVersions_UsingTwoBoms_ConfigProperties()
        throws Exception
    {
        System.out.println( "Complete repository test (two BOMs, config properties)..." );

        final File srcRepo = getResourceFile( "repository" );
        final File bom1 = getResourceFile( "bom.part1.xml" );
        final File bom2 = getResourceFile( "bom.part2.xml" );

        File config = folder.newFile( "config.properties" );
        config.deleteOnExit();

        List<String> lines = new ArrayList<String>();
        lines.add( "boms = " + bom1.getAbsolutePath() + ",\\" );
        lines.add( "        " + bom2.getAbsolutePath() );

        writeLines( config, lines );

        copyDirectory( srcRepo, repo );

        final String[] args = { "-Z", "-C", config.getPath(), repo.getPath() };

        Cli.main( args );
        assertExitValue();

        System.out.println( "\n\n" );
    }

    private void assertExitValue()
    {
        assertThat( new Integer( Cli.exitValue() ), equalTo( 0 ) );
    }

    @Test
    public void modifyPartialRepositoryVersions_UsingTwoBoms()
        throws Exception
    {
        System.out.println( "Partial repository test (two BOMs)..." );

        final File srcRepo = getResourceFile( "repository.partial" );
        final File bom1 = getResourceFile( "bom.part1.xml" );
        final File bom2 = getResourceFile( "bom.part2.xml" );

        copyDirectory( srcRepo, repo );

        modifyRepo( bom1, bom2 );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePom_BOMofBOMs()
        throws Exception
    {
        System.out.println( "BOM-of-BOMS test (normalize to BOM usage)..." );

        final File srcRepo = getResourceFile( "bom-of-boms" );
        copyDirectory( srcRepo, repo );

        final File pom = new File( repo, "project/pom.xml" );
        final File bom = new File( repo, "bom.xml" );
        File remoteRepo = new File( repo, "repo" );

        Properties props = new Properties();
        props.setProperty( Cli.REMOTE_REPOSITORY_PROPERTY, remoteRepo.toURI().normalize().toURL().toExternalForm() );
        props.setProperty( Cli.BOMS_LIST_PROPERTY, bom.getAbsolutePath() );

        File config = new File( repo, "vman.properties" );
        FileOutputStream out = null;
        try
        {
            out = new FileOutputStream( config );
            props.store( out, "bom-of-boms test" );
        }
        finally
        {
            closeQuietly( out );
        }

        final String[] args = { "-Z", "-C", config.getPath(), pom.getPath() };

        Cli.main( args );
        assertExitValue();

        assertNormalizedToBOMs( Collections.singleton( pom ), Collections.singleton( bom ) );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePom()
        throws Exception
    {
        System.out.println( "Single POM test..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bom = getResourceFile( "bom.xml" );

        File bomListing = writeBomList( bom );

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        File config = writeConfig( new Properties() );

        final String[] args = { "-Z", "-C", config.getPath(), "-b", bomListing.getPath(), pom.getPath() };

        Cli.main( args );
        assertExitValue();

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePom_CaptureMissing()
        throws Exception
    {
        System.out.println( "Single POM test with capture..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.missing.pom" );
        final File bom = getResourceFile( "bom.xml" );
        final File toolchain = getResourceFile( "empty-toolchain.pom" );

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        File capturePom = folder.newFile( "capture.pom" );

        Properties props = new Properties();
        props.setProperty( Cli.TOOLCHAIN_PROPERTY, toolchain.getAbsolutePath() );
        props.setProperty( Cli.BOMS_LIST_PROPERTY, bom.getAbsolutePath() );
        props.setProperty( Cli.CAPTURE_POM_PROPERTY, capturePom.getAbsolutePath() );

        File config = writeConfig( props );

        final String[] args = { "-Z", "-C", config.getPath(), pom.getPath() };

        Cli.main( args );

        System.out.println( "\n\n" );

        assertThat( capturePom.exists(), equalTo( true ) );
        Model model = loadModel( capturePom );
        new MavenXpp3Writer().write( System.out, model );
    }

    @Test
    public void modifySinglePom_ConfigProperties()
        throws Exception
    {
        System.out.println( "Single POM test (with config properties)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bom = getResourceFile( "bom.xml" );

        Properties props = new Properties();
        props.setProperty( "boms", bom.getAbsolutePath() );

        File config = writeConfig( props );

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        final String[] args = { "-Z", "-C", config.getPath(), pom.getPath() };

        Cli.main( args );
        assertExitValue();

        System.out.println( "\n\n" );
    }

    private File writeConfig( final Properties props )
        throws IOException
    {
        File config = folder.newFile( "config.properties" );
        config.deleteOnExit();

        FileOutputStream out = null;
        try
        {
            out = new FileOutputStream( config );
            props.store( out, "Generated during pom-version-manipulator unit tests." );
        }
        finally
        {
            closeQuietly( out );
        }

        return config;
    }

    @Test
    public void modifySinglePomUsingInterpolatedBOM()
        throws Exception
    {
        System.out.println( "Single POM test (interpolated BOM)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bom = getResourceFile( "bom.interp.xml" );

        File bomListing = writeBomList( bom );

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        File config = writeConfig( new Properties() );

        final String[] args = { "-Z", "-C", config.getPath(), "-b", bomListing.getPath(), pom.getPath() };

        Cli.main( args );
        assertExitValue();

        System.out.println( "\n\n" );
    }

    private File repo;

    @BeforeClass
    public static void enableClasspathScanning()
    {
        System.out.println( "Enabling classpath scanning..." );
        VersionManager.setClasspathScanning( true );
    }

    @BeforeClass
    public static void setupLogging()
    {
        LoggingFixture.setupLogging();
    }

    @Before
    public void setupDirs()
        throws IOException
    {
        repo = folder.newFolder( "repository" );
    }

    private void modifyRepo( final File... boms )
        throws Exception
    {
        File bomListing = writeBomList( boms );

        File config = writeConfig( new Properties() );

        final String[] baseArgs = { "-Z", "-C", config.getPath(), "-b", bomListing.getPath(), repo.getPath() };

        Cli.main( baseArgs );
        assertExitValue();
    }

    private File writeBomList( final File... boms )
        throws IOException
    {
        final List<String> bomList = new ArrayList<String>( boms.length );
        for ( final File bom : boms )
        {
            bomList.add( bom.getAbsolutePath() );
        }

        File bomListing = folder.newFile( "boms.lst" );
        bomListing.deleteOnExit();

        writeLines( bomListing, bomList );

        return bomListing;
    }

    private File getResourceFile( final String path )
    {
        final URL resource = Thread.currentThread().getContextClassLoader().getResource( path );
        if ( resource == null )
        {
            fail( "Resource not found: " + path );
        }

        return new File( resource.getPath() );
    }

}
