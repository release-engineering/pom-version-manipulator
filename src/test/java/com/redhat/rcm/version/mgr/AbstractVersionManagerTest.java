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

package com.redhat.rcm.version.mgr;

import static junit.framework.Assert.fail;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.spi.Configurator;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.maven.mae.MAEException;
import org.codehaus.plexus.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class AbstractVersionManagerTest
{

    protected VersionManager vman;

    protected final Set<File> toDelete = new HashSet<File>();

    protected File repo;

    protected File workspace;

    protected File reports;

    protected AbstractVersionManagerTest()
    {
    }

    @Before
    public void setupVersionManager()
        throws MAEException
    {
        vman = VersionManager.getInstance();
    }

    @BeforeClass
    public static void setupLogging()
    {
        System.out.println( "Setting up logging..." );
        final Configurator log4jConfigurator = new Configurator()
        {
            @Override
            @SuppressWarnings( "unchecked" )
            public void doConfigure( final URL notUsed, final LoggerRepository repo )
            {
                final ConsoleAppender appender = new ConsoleAppender( new SimpleLayout() );
                appender.setImmediateFlush( true );
                appender.setThreshold( Level.ALL );

                if ( !hasConsoleAppender( repo.getRootLogger() ) )
                {
                    repo.getRootLogger().addAppender( appender );
                }

                final Enumeration<Logger> loggers = repo.getCurrentLoggers();
                while ( loggers.hasMoreElements() )
                {
                    final Logger logger = loggers.nextElement();
                    if ( !hasConsoleAppender( logger ) )
                    {
                        logger.addAppender( appender );
                    }
                    logger.setLevel( Level.INFO );
                }
            }

            private boolean hasConsoleAppender( final Logger logger )
            {
                @SuppressWarnings( "unchecked" )
                final Enumeration<Appender> e = logger.getAllAppenders();

                while ( e.hasMoreElements() )
                {
                    if ( e.nextElement() instanceof ConsoleAppender )
                    {
                        return true;
                    }
                }

                return false;
            }
        };

        log4jConfigurator.doConfigure( null, LogManager.getLoggerRepository() );
    }

    @After
    public void deleteDirs()
    {
        if ( null == System.getProperty( "debug" ) )
        {
            for ( final File f : toDelete )
            {
                if ( f.exists() )
                {
                    try
                    {
                        FileUtils.forceDelete( f );
                    }
                    catch ( final IOException e )
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Before
    public void setupDirs()
        throws IOException
    {
        repo = createTempDir( "repository" );
        workspace = createTempDir( "workspace" );
        reports = createTempDir( "reports" );
    }

    protected void assertNoErrors( VersionManagerSession session )
    {
        Map<File, Set<Throwable>> errors = session.getErrors();
        if ( errors != null && !errors.isEmpty() )
        {
            for ( Map.Entry<File, Set<Throwable>> entry : errors.entrySet() )
            {
                System.out.printf( "%d errors encountered while processing file: %s\n\n",
                                   entry.getValue().size(),
                                   entry.getKey() );
                for ( Throwable error : entry.getValue() )
                {
                    error.printStackTrace();
                }
            }

            fail( "See above errors." );
        }
    }

    protected VersionManagerSession modifyRepo( final String... boms )
    {
        final VersionManagerSession session = newVersionManagerSession();

        vman.modifyVersions( repo, "**/*.pom", Arrays.asList( boms ), null, session );
        assertNoErrors( session );
        vman.generateReports( reports, session );

        return session;
    }

    protected VersionManagerSession newVersionManagerSession()
    {
        return new VersionManagerSession( workspace, reports, false, false, false );
    }

    protected File createTempDir( final String basename )
        throws IOException
    {
        final File temp = File.createTempFile( basename, ".dir" );
        temp.delete();

        temp.mkdirs();

        toDelete.add( temp );

        return temp;
    }

    protected File getResourceFile( final String path )
    {
        final URL resource = Thread.currentThread().getContextClassLoader().getResource( path );
        if ( resource == null )
        {
            fail( "Resource not found: " + path );
        }

        return new File( resource.getPath() );
    }

}