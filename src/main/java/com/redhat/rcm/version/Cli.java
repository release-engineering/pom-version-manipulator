/*
 *  Copyright (C) 2010 John Casey.
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

package com.redhat.rcm.version;

import org.commonjava.emb.EMBException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ExampleMode;
import org.kohsuke.args4j.Option;

import java.io.File;

public class Cli
{

    private static final String PATTERN_PREFIX = "**/";

    @Argument( index = 0, usage = "POM file (or directory containing POM files) which should be modified.", required = true )
    private File target;

    @Argument( index = 1, usage = "Bill-of-Materials POM file supplying versions.", required = true )
    private File bom;

    @Option( name = "-p", usage = "POM path pattern (glob)" )
    private String pomPattern = PATTERN_PREFIX + "*.pom";

    @Option( name = "-D", usage = "Don't rename directories" )
    private final boolean preserveDirs = false;

    @Option( name = "-b", usage = "Location where original files should be backed up before modifying." )
    private File backups;

    @Option( name = "-h", usage = "Print this help message and quit" )
    private boolean help = false;

    public static void main( final String[] args )
        throws EMBException
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

    public Cli( final File target, final File bom )
    {
        this.target = target;
        this.bom = bom;
    }

    public Cli()
    {
    }

    public void run()
        throws EMBException
    {
        final VersionManipulator vman = new VersionManipulator();
        final ManipulationSession session = new ManipulationSession( backups, preserveDirs );
        if ( target.isDirectory() )
        {
            vman.modifyVersions( target, pomPattern, bom, session );
        }
        else
        {
            vman.modifyVersions( bom, bom, session );
        }

        // TODO: Run reports on the session!
    }

    private static void printUsage( final CmdLineParser parser, final CmdLineException error )
    {
        if ( error != null )
        {
            System.err.println( "Invalid option(s): " + error.getMessage() );
            System.err.println();
        }

        parser.printUsage( System.err );
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

}
