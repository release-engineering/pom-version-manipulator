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

import static org.apache.commons.io.IOUtils.closeQuietly;

import org.apache.log4j.Logger;
import org.apache.maven.mae.MAEException;
import org.codehaus.plexus.util.StringUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ExampleMode;
import org.kohsuke.args4j.Option;

import com.redhat.rcm.version.mgr.VersionManager;
import com.redhat.rcm.version.mgr.VersionManagerSession;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Cli
{
    @Argument( index = 0, metaVar = "target", usage = "POM file (or directory containing POM files) to modify." )
    private File target;

    @Argument( index = 1, metaVar = "BOM", usage = "Bill-of-Materials POM file supplying versions.", multiValued = true )
    private List<String> boms;

    @Option( name = "-b", usage = "File containing a list of BOMs to use (instead of listing them on the command line)" )
    private File bomList;

    @Option( name = "-p", usage = "POM path pattern (glob)" )
    private String pomPattern = "**/*.pom";

    @Option( name = "-P", aliases = { "--preserve" }, usage = "Write changed POMs back to original input files" )
    private final boolean preserveFiles = false;

    @Option( name = "-w", aliases = { "--workspace" }, usage = "Backup original files here up before modifying." )
    private final File workspace = new File( "vman-workspace" );

    @Option( name = "-r", aliases = { "--report-dir" }, usage = "Write reports here." )
    private final File reports = new File( "vman-reports" );

    @Option( name = "-n", aliases = { "--normalize" }, usage = "Normalize the BOM usage (introduce the BOM if not already there, and defer all dependency versions to that)." )
    private boolean normalizeBomUsage = false;

    @Option( name = "-h", aliases = { "--help" }, usage = "Print this message and quit" )
    private boolean help = false;

    private static final Logger LOGGER = Logger.getLogger( Cli.class );

    private static VersionManager vman;

    public static void main( final String[] args )
        throws Exception
    {
        final Cli cli = new Cli();
        final CmdLineParser parser = new CmdLineParser( cli );
        try
        {
            parser.parseArgument( args );

            if ( cli.help )
            {
                printUsage( parser, null );
            }
            else if ( cli.target == null || ( cli.boms == null && cli.bomList == null ) )
            {
                printUsage( parser, null );
            }
            else
            {
                cli.run();
            }
        }
        catch ( final CmdLineException error )
        {
            printUsage( parser, error );
        }
    }

    public Cli( final File target, final String... boms )
        throws MAEException
    {
        this.target = target;
        this.boms = new ArrayList<String>( Arrays.asList( boms ) );
    }

    public Cli( final File target, final File bomList )
        throws MAEException
    {
        this.target = target;
        this.bomList = bomList;
        this.boms = new ArrayList<String>();
    }

    public Cli()
        throws MAEException
    {
    }

    public void run()
        throws MAEException, VManException
    {
        vman = VersionManager.getInstance();
        
        final VersionManagerSession session = new VersionManagerSession( workspace, preserveFiles, normalizeBomUsage );
        if ( bomList != null )
        {
            loadBomList( session );
        }

        LOGGER.info( "Modifying POM(s).\n\nTarget:\n\t" + target + "\n\nBOMs:\n\t"
            + StringUtils.join( boms.iterator(), "\n\t" ) + "\n\nWorkspace:\n\t" + workspace + "\n\nReports:\n\t"
            + reports );

        if ( target.isDirectory() )
        {
            vman.modifyVersions( target, pomPattern, boms, session );
        }
        else
        {
            vman.modifyVersions( target, boms, session );
        }

        reports.mkdirs();
        vman.generateReports( reports, session );
    }

    private boolean loadBomList( VersionManagerSession session )
    {
        boolean success = true;
        if ( bomList != null && bomList.canRead() )
        {
            BufferedReader reader = null;
            try
            {
                reader = new BufferedReader( new FileReader( bomList ) );
                String line = null;
                while( ( line = reader.readLine() ) != null )
                {
                    boms.add( line.trim() );
                }
            }
            catch ( IOException e )
            {
                session.addGlobalError( e );
                success = false;
            }
            finally
            {
                closeQuietly( reader );
            }
        }
        else
        {
            LOGGER.error( "No such BOM list file: '" + bomList + "'." );
        }
        
        return success;
    }

    private static void printUsage( final CmdLineParser parser, final CmdLineException error )
    {
        if ( error != null )
        {
            System.err.println( "Invalid option(s): " + error.getMessage() );
            System.err.println();
        }

        parser.printUsage( System.err );
        System.err.println( "Usage: $0 [OPTIONS] <target-path> <BOM-path>" );
        System.err.println();
        System.err.println();
        System.err.println( parser.printExample( ExampleMode.ALL ) );
        System.err.println();
    }

    protected String getPomPattern()
    {
        return pomPattern;
    }

    protected void setPomPattern( final String pomPattern )
    {
        this.pomPattern = pomPattern;
    }

    protected boolean isHelp()
    {
        return help;
    }

    protected void setHelp( final boolean help )
    {
        this.help = help;
    }

    protected boolean isNormalizeBomUsage()
    {
        return normalizeBomUsage;
    }

    protected void setNormalizeBomUsage( final boolean normalizeBomUsage )
    {
        this.normalizeBomUsage = normalizeBomUsage;
    }

}
