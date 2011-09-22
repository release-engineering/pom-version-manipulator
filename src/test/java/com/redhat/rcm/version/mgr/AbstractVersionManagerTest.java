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

import static com.redhat.rcm.version.testutil.TestProjectUtils.getResourceFile;
import static junit.framework.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.spi.Configurator;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.maven.mae.MAEException;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public abstract class AbstractVersionManagerTest
{

    protected static final String TOOLCHAIN = "toolchain/toolchain-1.0.pom";

    protected VersionManager vman;

    protected ConsoleAppender appender = new ConsoleAppender( new SimpleLayout() );

    protected File repo;

    protected File workspace;

    protected File reports;

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    protected AbstractVersionManagerTest()
    {
    }

    public void setupVersionManager()
        throws MAEException
    {
        if ( vman == null )
        {
            vman = VersionManager.getInstance();
        }
    }

    public synchronized void setupDirs()
        throws IOException
    {
        if ( repo == null )
        {
            repo = tempFolder.newFolder( "repository" );
        }

        if ( workspace == null )
        {
            workspace = tempFolder.newFolder( "workspace" );
        }

        if ( reports == null )
        {
            reports = tempFolder.newFolder( "reports" );
        }
    }

    protected synchronized void flushLogging()
    {
        System.out.flush();
        System.err.flush();
        if ( appender != null )
        {
            appender.close();
            appender = null;
        }
    }

    protected void setupLogging( final Map<Class<?>, Level> levels )
    {
        System.out.println( "Setting up logging..." );
        final Configurator log4jConfigurator = new Configurator()
        {
            @Override
            @SuppressWarnings( "unchecked" )
            public void doConfigure( final URL notUsed, final LoggerRepository repo )
            {
                Level defaultLevel = Level.ERROR;

                // appender.setImmediateFlush( true );
                appender.setThreshold( Level.TRACE );

                repo.getRootLogger().removeAllAppenders();
                repo.getRootLogger().addAppender( appender );
                repo.getRootLogger().setLevel( defaultLevel );

                Set<String> processed = new HashSet<String>();
                if ( levels != null )
                {
                    for ( Map.Entry<Class<?>, Level> entry : levels.entrySet() )
                    {
                        String name = entry.getKey().getName();

                        Logger logger = repo.getLogger( name );
                        if ( logger != null )
                        {
                            logger.removeAllAppenders();
                            logger.addAppender( appender );
                            logger.setLevel( entry.getValue() );
                        }

                        processed.add( name );
                    }
                }

                final Enumeration<Logger> loggers = repo.getCurrentLoggers();
                while ( loggers.hasMoreElements() )
                {
                    final Logger logger = loggers.nextElement();
                    String name = logger.getName();

                    if ( !processed.contains( name ) )
                    {
                        logger.removeAllAppenders();
                        logger.addAppender( appender );

                        logger.setLevel( defaultLevel );
                        processed.add( name );
                    }
                }
            }
        };

        log4jConfigurator.doConfigure( null, LogManager.getLoggerRepository() );
    }

    protected void assertNoErrors( final VersionManagerSession session )
    {
        List<Throwable> errors = session.getErrors();
        if ( errors != null && !errors.isEmpty() )
        {
            StringBuilder sb = new StringBuilder();
            sb.append( errors.size() ).append( "errors encountered\n\n" );

            int idx = 1;
            for ( Throwable error : errors )
            {
                StringWriter sw = new StringWriter();
                error.printStackTrace( new PrintWriter( sw ) );

                sb.append( "\n" ).append( idx ).append( ".  " ).append( sw.toString() );
                idx++;
            }

            sb.append( "\n\nSee above errors." );

            fail( sb.toString() );
        }
    }

    protected String getToolchainPath()
    {
        return getResourceFile( TOOLCHAIN ).getAbsolutePath();
    }

    protected VersionManagerSession modifyRepo( final boolean useToolchain, final String... boms )
    {
        final VersionManagerSession session = newVersionManagerSession();

        vman.modifyVersions( repo,
                             "**/*.pom",
                             Arrays.asList( boms ),
                             useToolchain ? getToolchainPath() : null,
                             null,
                             session );
        assertNoErrors( session );
        vman.generateReports( reports, session );

        return session;
    }

    protected VersionManagerSession newVersionManagerSession()
    {
        return new VersionManagerSession( workspace, reports, null, false );
    }

}