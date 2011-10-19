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

import static com.redhat.rcm.version.testutil.VManAssertions.assertNormalizedToBOMs;
import static junit.framework.Assert.fail;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.io.FileUtils.forceDelete;
import static org.apache.commons.io.FileUtils.writeLines;
import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.redhat.rcm.version.fixture.LoggingFixture;
import com.redhat.rcm.version.mgr.VersionManager;

public class CliTest
{

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

        File config = File.createTempFile( "config.", ".properties" );
        config.deleteOnExit();

        List<String> lines = new ArrayList<String>();
        lines.add( "boms = " + bom1.getAbsolutePath() + ",\\" );
        lines.add( "        " + bom2.getAbsolutePath() );

        writeLines( config, lines );

        copyDirectory( srcRepo, repo );

        final String[] args = { "-C", config.getPath(), repo.getPath() };

        Cli.main( args );

        System.out.println( "\n\n" );
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

    // FIXME: Adapt this, to make sure the CLI will operate properly...
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

        final String[] args = { "-C", config.getPath(), pom.getPath() };

        Cli.main( args );

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

        final String[] args = { "-b", bomListing.getPath(), pom.getPath() };

        Cli.main( args );

        System.out.println( "\n\n" );
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

        File config = File.createTempFile( "config.", ".properties" );
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

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        final String[] args = { "-C", config.getPath(), pom.getPath() };

        Cli.main( args );

        System.out.println( "\n\n" );
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

        final String[] args = { "-b", bomListing.getPath(), pom.getPath() };

        Cli.main( args );

        System.out.println( "\n\n" );
    }

    private static final Set<File> toDelete = new HashSet<File>();

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

    @AfterClass
    public static void deleteDirs()
    {
        for ( final File f : toDelete )
        {
            if ( f.exists() )
            {
                try
                {
                    forceDelete( f );
                }
                catch ( final IOException e )
                {
                    e.printStackTrace();
                }
            }
        }
    }

    @Before
    public void setupDirs()
        throws IOException
    {
        repo = createTempDir( "repository" );
    }

    private void modifyRepo( final File... boms )
        throws Exception
    {
        File bomListing = writeBomList( boms );

        final String[] baseArgs = { "-b", bomListing.getPath(), repo.getPath() };

        Cli.main( baseArgs );
    }

    private File writeBomList( final File... boms )
        throws IOException
    {
        final List<String> bomList = new ArrayList<String>( boms.length );
        for ( final File bom : boms )
        {
            bomList.add( bom.getAbsolutePath() );
        }

        File bomListing = File.createTempFile( "boms.", ".lst" );
        bomListing.deleteOnExit();

        writeLines( bomListing, bomList );

        return bomListing;
    }

    private File createTempDir( final String basename )
        throws IOException
    {
        final File temp = File.createTempFile( basename, ".dir" );
        temp.delete();

        temp.mkdirs();

        toDelete.add( temp );

        return temp;
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
