/*
 * Copyright (c) 2012 Red Hat, Inc.
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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.mae.MAEException;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.StringUtils;

import com.redhat.rcm.version.config.VManConfiguration;
import com.redhat.rcm.version.mgr.VersionManager;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

@Component( role = AbstractMavenLifecycleParticipant.class, hint = "vman" )
public class LifecycleParticipant
    extends AbstractMavenLifecycleParticipant
{
    private static final Logger LOGGER = Logger.getLogger( LifecycleParticipant.class );

    @Requirement
    private VersionManager vman;

    private VManConfiguration config;

    private int exitValue;

    public int run()
        throws MAEException, VManException, MalformedURLException
    {
        config.configureLogging();

        final VersionManagerSession session = config.initSession( vman );

        if ( !session.hasBoms() )
        {
            LOGGER.error( "You must specify at least one BOM." );
            return -2;
        }

        if ( session.getErrors()
                    .isEmpty() )
        {
            LOGGER.info( "Modifying POM(s).\n\nTarget:\n\t" + config.getTarget() + "\n\nBOMs:\n\t"
                + StringUtils.join( config.getBoms()
                                          .iterator(), "\n\t" ) + "\n\nWorkspace:\n\t" + config.getWorkspace()
                + "\n\nReports:\n\t" + config.getReports() );

            if ( config.getTarget()
                       .isDirectory() )
            {
                vman.modifyVersions( config.getTarget(), config.getPomPattern(), config.getPomExcludePattern(),
                                     config.getBoms(), config.getToolchain(), session );
            }
            else
            {
                vman.modifyVersions( config.getTarget(), config.getBoms(), config.getToolchain(), session );
            }
        }

        config.getReports()
              .mkdirs();
        vman.generateReports( config.getReports(), session );

        if ( config.getCapturePom() != null && config.getCapturePom()
                                                     .exists() )
        {
            LOGGER.warn( "\n\n\n\n\nMissing dependency/plugin information has been captured in:\n\n\t"
                + config.getCapturePom()
                        .getAbsolutePath() + "\n\n\n\n" );

            return -1;
        }
        else
        {
            final List<Throwable> errors = session.getErrors();
            if ( errors != null && !errors.isEmpty() )
            {
                LOGGER.error( errors.size() + " errors detected!\n\n" );

                int i = 1;
                for ( final Throwable error : errors )
                {
                    LOGGER.error( "\n\n" + i, error );
                    i++;
                }

                return -1;
            }
        }

        return 0;
    }

    @Override
    public void afterProjectsRead( final MavenSession session )
        throws MavenExecutionException
    {
        super.afterProjectsRead( session );

        // TODO: We need to modify the projects ON-THE-FLY AS THEY ARE READ, NOT ON DISK...right??

        // FIXME: Hackish, it'd be nice if we had a better way to extract/inject settings.
        final String[] args = formatArgs( session );

        config = new VManConfiguration();

        boolean doExit = false;
        try
        {
            final boolean run = config.parse( args );

            if ( run )
            {
                exitValue = run();
                doExit = exitValue != 0;
            }
            else
            {
                doExit = true;
            }
        }
        catch ( final MAEException e )
        {
            config.printUsage( e );
            doExit = true;
        }
        catch ( final MalformedURLException e )
        {
            config.printUsage( e );
            doExit = true;
        }
        catch ( final VManException e )
        {
            config.printUsage( e );
            doExit = true;
        }

        if ( doExit )
        {
            // FIXME: Probably need a more elegant approach...
            System.exit( exitValue );
        }
    }

    private String[] formatArgs( final MavenSession session )
    {
        final Properties props = new Properties();

        if ( session.getSystemProperties() != null )
        {
            props.putAll( session.getSystemProperties() );
        }

        if ( session.getUserProperties() != null )
        {
            props.putAll( session.getUserProperties() );
        }

        final List<String> args = new ArrayList<String>();
        for ( final Object name : Collections.list( props.propertyNames() ) )
        {
            final String prop = (String) name;
            if ( prop.length() > 5 && prop.startsWith( "vman." ) )
            {
                final String value = props.getProperty( prop );
                final String arg = "--" + prop.substring( 5 ) + "=" + value;
                args.add( arg );
            }
        }
        return args.toArray( new String[] {} );
    }

    @Override
    public void afterSessionStart( final MavenSession session )
        throws MavenExecutionException
    {
        // TODO OVERRIDE THE MODEL BUILDER WITH PREFERRED PARENT VERSION(S)
        super.afterSessionStart( session );
    }
}
