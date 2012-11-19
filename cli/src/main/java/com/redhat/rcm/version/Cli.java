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
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.maven.mae.MAEException;
import org.apache.maven.mae.app.AbstractMAEApplication;
import org.apache.maven.mae.boot.embed.MAEEmbedderBuilder;
import org.apache.maven.mae.internal.container.ComponentSelector;
import org.apache.maven.mae.project.session.SessionInitializer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.StringUtils;

import com.redhat.rcm.version.config.VManConfiguration;
import com.redhat.rcm.version.maven.VManSessionInitializer;
import com.redhat.rcm.version.mgr.VersionManager;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

@Component( role = Cli.class )
public class Cli
    extends AbstractMAEApplication
{

    private static final Logger LOGGER = Logger.getLogger( Cli.class );

    @Requirement
    private VersionManager vman;

    private final VManConfiguration config;

    private static Integer exitValue;

    private static boolean classpathScanning;

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

    public static void main( final String[] args )
    {
        final VManConfiguration config = new VManConfiguration();
        boolean doExit = false;

        try
        {
            final boolean run = config.parse( args );

            if ( run )
            {
                final Cli cli = new Cli( config );
                cli.load();
                exitValue = cli.run();
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

    public static int getExitValue()
    {
        return exitValue;
    }

    public Cli( final VManConfiguration config )
    {
        this.config = config;
    }

    @Override
    public String getId()
    {
        return getName();
    }

    @Override
    public String getName()
    {
        return "VMan";
    }

    public static void setClasspathScanning( final boolean scanning )
    {
        classpathScanning = scanning;
    }

    @Override
    protected void configureBuilder( final MAEEmbedderBuilder builder )
        throws MAEException
    {
        super.configureBuilder( builder );
        builder.withClassScanningEnabled( classpathScanning );
    }

    @Override
    public ComponentSelector getComponentSelector()
    {
        return new ComponentSelector().setSelection( SessionInitializer.class, VManSessionInitializer.HINT );
    }
}
