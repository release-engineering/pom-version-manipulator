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

import static com.redhat.rcm.version.testutil.TestProjectFixture.loadModel;
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
import java.lang.reflect.Field;
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
import org.junit.rules.TestName;

import com.redhat.rcm.version.fixture.LoggingFixture;
import com.redhat.rcm.version.mgr.VersionManager;
import com.redhat.rcm.version.testutil.HttpTestService;

public class CliTest
{

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public TestName name = new TestName();

    public void help()
        throws Exception
    {
        Cli.main( new String[] { "-h" } );
    }

    /*
     * Validate the exit value. Use reflection to retrieve the value to avoid
     * having to create unnecessary accessors.
     */
    private void assertExitValue()
    {
        try
        {
            final Field f = Cli.class.getDeclaredField( "exitValue" );
            f.setAccessible( true );
            assertThat( f.getInt( null ), equalTo( 0 ) );
        }
        catch ( final SecurityException e )
        {
            fail( "Exception retrieving field information " + e );
        }
        catch ( final NoSuchFieldException e )
        {
            fail( "Exception retrieving field information " + e );
        }
        catch ( final IllegalArgumentException e )
        {
            fail( "Exception retrieving field information " + e );
        }
        catch ( final IllegalAccessException e )
        {
            fail( "Exception retrieving field information " + e );
        }
    }

    @Test
    public void modify_BOMofBOMs()
        throws Exception
    {
        System.out.println( "BOM-of-BOMS test (normalize to BOM usage)..." );

        final File srcRepo = getResourceFile( "bom-of-boms" );
        copyDirectory( srcRepo, repo );

        final File pom = new File( repo, "project/pom.xml" );
        final File bom = new File( repo, "bom.xml" );
        final File remoteRepo = new File( repo, "repo" );

        final Properties props = new Properties();
        props.setProperty( Cli.REMOTE_REPOSITORIES_PROPERTY, remoteRepo.toURI()
                                                                       .normalize()
                                                                       .toURL()
                                                                       .toExternalForm() );
        props.setProperty( Cli.BOMS_LIST_PROPERTY, bom.getAbsolutePath() );

        final File config = new File( repo, "vman.properties" );
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

        // FIXME
        //        assertPOMsNormalizedToBOMs( Collections.singleton( pom ), Collections.singleton( bom ), session, fixture );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePom()
        throws Exception
    {
        System.out.println( "Single POM test..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bom = getResourceFile( "bom.xml" );

        final File bomListing = writeBomList( bom );

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        final File config = writeConfig( new Properties() );

        final String[] args = { "-Z", "-C", config.getPath(), "-b", bomListing.getPath(), pom.getPath() };

        Cli.main( args );
        assertExitValue();

        System.out.println( "\n\n" );
    }

    @Test
    public void modify_CaptureMissing()
        throws Exception
    {
        System.out.println( "Single POM test with capture..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.missing.pom" );
        final File bom = getResourceFile( "bom.xml" );
        final File toolchain = getResourceFile( "empty-toolchain.pom" );

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        final File capturePom = folder.newFile( "capture.pom" );

        final Properties props = new Properties();
        props.setProperty( Cli.TOOLCHAIN_PROPERTY, toolchain.getAbsolutePath() );
        props.setProperty( Cli.BOMS_LIST_PROPERTY, bom.getAbsolutePath() );
        props.setProperty( Cli.CAPTURE_POM_PROPERTY, capturePom.getAbsolutePath() );

        final File config = writeConfig( props );

        final String[] args = { "-Z", "-C", config.getPath(), pom.getPath() };

        Cli.main( args );

        System.out.println( "\n\n" );

        assertThat( capturePom.exists(), equalTo( true ) );
        final Model model = loadModel( capturePom );
        new MavenXpp3Writer().write( System.out, model );
    }

    @Test
    public void modify_ConfigProperties()
        throws Exception
    {
        System.out.println( "Single POM test (with config properties)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bom = getResourceFile( "bom.xml" );

        final Properties props = new Properties();
        props.setProperty( "boms", bom.getAbsolutePath() );

        final File config = writeConfig( props );

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        final String[] args = { "-Z", "-C", config.getPath(), pom.getPath() };

        Cli.main( args );
        assertExitValue();

        System.out.println( "\n\n" );
    }

    @Test
    public void modify_HTTPConfigProperties()
        throws Exception
    {
        System.out.println( "Single POM test (with http config properties)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        final String[] args = { "-Z", "-C", "http://localhost/vman.properties", pom.getPath() };

        Cli.main( args );
        assertExitValue();

        System.out.println( "\n\n" );
    }

    @Test
    public void modify_ConfigProperties_FromBootstrapPath()
        throws Exception
    {
        System.out.println( "Single POM test (with config properties from file path in bootstrap)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bom = getResourceFile( "bom.xml" );

        Properties props = new Properties();
        props.setProperty( "boms", bom.getAbsolutePath() );

        final File config = writeConfig( props );

        final File bootstrap = getResourceFile( Cli.BOOTSTRAP_PROPERTIES );
        props = new Properties();
        props.setProperty( Cli.BOOT_CONFIG_PROPERTY, config.getAbsolutePath() );

        writeConfigTo( props, bootstrap );

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        final String[] args = { "-Z", pom.getPath() };

        Cli.main( args );
        assertExitValue();

        System.out.println( "\n\n" );
    }

    @Test
    public void modify_ConfigProperties_FromBootstrapURL()
        throws Exception
    {
        System.out.println( "Single POM test (with config properties from file path in bootstrap)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bom = getResourceFile( "bom.xml" );

        Properties props = new Properties();
        props.setProperty( "boms", bom.getAbsolutePath() );

        final File config = writeConfig( props );

        final HttpTestService http =
            new HttpTestService( Collections.singletonMap( "/bootstrap.properties", config.toURI()
                                                                                          .toURL() ) );
        try
        {
            String baseUrl = null;
            try
            {
                baseUrl = http.start();
            }
            catch ( final Exception e )
            {
                fail( "Failed to start HTTP service..." );
            }

            final File bootstrap = getResourceFile( Cli.BOOTSTRAP_PROPERTIES );
            props = new Properties();
            props.setProperty( Cli.BOOT_CONFIG_PROPERTY, baseUrl + "/bootstrap.properties" );

            writeConfigTo( props, bootstrap );

            final File pom = new File( repo, srcPom.getName() );
            copyFile( srcPom, pom );

            final String[] args = { "-Z", pom.getPath() };

            Cli.main( args );
            assertExitValue();
        }
        finally
        {
            if ( http != null )
            {
                http.stop();
            }
        }

        System.out.println( "\n\n" );
    }

    @Test
    public void modify_ConfigProperties_FromBootstrapPath_UsingBootstrapOption()
        throws Exception
    {
        System.out.println( "Single POM test (with config properties from file path in bootstrap)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bom = getResourceFile( "bom.xml" );

        Properties props = new Properties();
        props.setProperty( "boms", bom.getAbsolutePath() );

        final File config = writeConfig( props );

        props = new Properties();
        props.setProperty( Cli.BOOT_CONFIG_PROPERTY, config.getAbsolutePath() );

        final File bootstrap = writeConfig( props, "bootstrap.properties" );

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        final String[] args = { "-Z", "-B", bootstrap.getAbsolutePath(), pom.getPath() };

        Cli.main( args );
        assertExitValue();

        System.out.println( "\n\n" );
    }

    @Test
    public void modify_ConfigProperties_FromBootstrapURL_UsingBootstrapOption()
        throws Exception
    {
        System.out.println( "Single POM test (with config properties from file path in bootstrap)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bom = getResourceFile( "bom.xml" );

        Properties props = new Properties();
        props.setProperty( "boms", bom.getAbsolutePath() );

        final File config = writeConfig( props );

        final HttpTestService http =
            new HttpTestService( Collections.singletonMap( "/bootstrap.properties", config.toURI()
                                                                                          .toURL() ) );
        try
        {
            String baseUrl = null;
            try
            {
                baseUrl = http.start();
            }
            catch ( final Exception e )
            {
                fail( "Failed to start HTTP service..." );
            }

            props = new Properties();
            props.setProperty( Cli.BOOT_CONFIG_PROPERTY, baseUrl + "/bootstrap.properties" );

            final File bootstrap = writeConfig( props, "bootstrap.properties" );

            final File pom = new File( repo, srcPom.getName() );
            copyFile( srcPom, pom );

            final String[] args = { "-Z", "-B", bootstrap.getAbsolutePath(), pom.getPath() };

            Cli.main( args );
            assertExitValue();
        }
        finally
        {
            if ( http != null )
            {
                http.stop();
            }
        }

        System.out.println( "\n\n" );
    }

    private File writeConfig( final Properties props )
        throws IOException
    {
        return writeConfig( props, "config.properties" );
    }

    private File writeConfig( final Properties props, final String name )
        throws IOException
    {
        final File config = folder.newFile( name );
        config.deleteOnExit();

        writeConfigTo( props, config );

        return config;
    }

    private void writeConfigTo( final Properties props, final File config )
        throws IOException
    {
        FileOutputStream out = null;
        try
        {
            out = new FileOutputStream( config );
            props.store( out, "Generated for test: " + getClass().getName() + "#" + name.getMethodName() );
        }
        finally
        {
            closeQuietly( out );
        }
    }

    @Test
    public void modifySinglePomUsingInterpolatedBOM()
        throws Exception
    {
        System.out.println( "Single POM test (interpolated BOM)..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bom = getResourceFile( "bom.interp.xml" );

        final File bomListing = writeBomList( bom );

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        final File config = writeConfig( new Properties() );

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

    private File writeBomList( final File... boms )
        throws IOException
    {
        final List<String> bomList = new ArrayList<String>( boms.length );
        for ( final File bom : boms )
        {
            bomList.add( bom.getAbsolutePath() );
        }

        final File bomListing = folder.newFile( "boms.lst" );
        bomListing.deleteOnExit();

        writeLines( bomListing, bomList );

        return bomListing;
    }

    private File getResourceFile( final String path )
    {
        final URL resource = Thread.currentThread()
                                   .getContextClassLoader()
                                   .getResource( path );
        if ( resource == null )
        {
            fail( "Resource not found: " + path );
        }

        return new File( resource.getPath() );
    }

}
