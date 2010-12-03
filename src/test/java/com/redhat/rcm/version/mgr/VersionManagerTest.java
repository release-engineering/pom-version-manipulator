/*
 * Copyright (c) 2010 Red Hat, Inc.
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

import static junit.framework.Assert.fail;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.spi.Configurator;
import org.apache.log4j.spi.LoggerRepository;
import org.codehaus.plexus.util.FileUtils;
import org.commonjava.emb.EMBException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class VersionManagerTest
{

    private static VersionManager vman;

    private static final Set<File> toDelete = new HashSet<File>();

    private File repo;

    private File backups;

    private File reports;

    @BeforeClass
    public static void setupVersionManager()
        throws EMBException
    {
        vman = new VersionManager();
    }

    @BeforeClass
    public static void setupLogging()
    {
        final Configurator log4jConfigurator = new Configurator()
        {
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

    @AfterClass
    public static void deleteDirs()
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
        backups = createTempDir( "backup-repo" );
        reports = createTempDir( "reports" );
    }

    @Test
    public void modifyCompleteRepositoryVersions()
        throws IOException
    {
        System.out.println( "Complete repository test..." );

        final File srcRepo = getResourceFile( "repository" );
        final File bom = getResourceFile( "bom/pom.xml" );

        FileUtils.copyDirectoryStructure( srcRepo, repo );

        modifyRepo( bom );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifyPartialRepositoryVersions()
        throws IOException
    {
        System.out.println( "Partial repository test..." );

        final File srcRepo = getResourceFile( "repository.partial" );
        final File bom = getResourceFile( "bom/pom.xml" );

        FileUtils.copyDirectoryStructure( srcRepo, repo );

        modifyRepo( bom );

        System.out.println( "\n\n" );
    }

    @Test
    public void modifySinglePom()
        throws IOException
    {
        System.out.println( "Single POM test..." );

        final File srcPom = getResourceFile( "rwx-parent-0.2.1.pom" );
        final File bom = getResourceFile( "bom/pom.xml" );

        final File pom = new File( repo, srcPom.getName() );
        FileUtils.copyFile( srcPom, pom );

        final VersionManagerSession session = new VersionManagerSession( backups, false );

        vman.modifyVersions( pom, bom, session );
        vman.generateReports( reports, session );

        System.out.println( "\n\n" );
    }

    private VersionManagerSession modifyRepo( final File bom )
    {
        final VersionManagerSession session = new VersionManagerSession( backups, false );

        vman.modifyVersions( repo, "**/*.pom", bom, session );
        vman.generateReports( reports, session );

        return session;
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
