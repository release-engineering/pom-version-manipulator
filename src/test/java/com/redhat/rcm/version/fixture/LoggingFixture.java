package com.redhat.rcm.version.fixture;

import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.spi.Configurator;
import org.apache.log4j.spi.LoggerRepository;

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
        loggingSetup = true;
    }

    public static synchronized void flushLogging()
    {
        System.out.flush();
        System.err.flush();
        if ( appender != null )
        {
            appender.close();
            appender = null;
        }
    }

}
