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

package com.redhat.rcm.version.fixture;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.spi.Configurator;
import org.apache.log4j.spi.LoggerRepository;

import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LoggingFixture
{
    private static boolean loggingSetup = false;

    private static ConsoleAppender appender = new ConsoleAppender( new SimpleLayout() );

    public static void setupLogging()
    {
        setupLogging( Collections.<Class<?>, Level> emptyMap() );
    }

    public static void setupLogging( final Map<Class<?>, Level> levels )
    {
        if ( loggingSetup )
        {
            return;
        }

        System.out.println( "Setting up logging..." );
        final Configurator log4jConfigurator = new Configurator()
        {
            @Override
            @SuppressWarnings( "unchecked" )
            public void doConfigure( final URL notUsed, final LoggerRepository repo )
            {
                final Level defaultLevel = Level.DEBUG;

                // appender.setImmediateFlush( true );
                appender.setThreshold( Level.TRACE );

                repo.getRootLogger().removeAllAppenders();
                repo.getRootLogger().addAppender( appender );
                repo.getRootLogger().setLevel( defaultLevel );

                final Set<String> processed = new HashSet<String>();
                if ( levels != null )
                {
                    for ( final Map.Entry<Class<?>, Level> entry : levels.entrySet() )
                    {
                        final String name = entry.getKey().getName();

                        final Logger logger = repo.getLogger( name );
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
                    final String name = logger.getName();

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
        loggingSetup = true;
    }

    public static synchronized void flushLogging()
    {
        System.out.flush();
        System.err.flush();
        // if ( appender != null )
        // {
        // appender.close();
        // appender = null;
        // }
    }

}
