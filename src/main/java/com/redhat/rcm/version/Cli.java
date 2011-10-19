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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

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

public class Cli
{
    @Argument( index = 0, metaVar = "target", usage = "POM file (or directory containing POM files) to modify." )
    private File target = new File( System.getProperty( "user.dir" ), "pom.xml" );

    @Option( name = "-r", aliases = "--rm-plugins", usage = "List of plugins (format: <groupId:artifactId>[,<groupId:artifactId>]) to REMOVE if found" )
    private String removedPluginsList;

    @Option( name = "-b", aliases = "--boms", usage = "File containing a list of BOM URLs to use for standardizing dependencies" )
    private File bomList;

    @Option( name = "-t", aliases = "--toolchain", usage = "Toolchain POM URL, containing standard plugin versions in the build/pluginManagement section, "
        + "and plugin injections in the regular build/plugins section." )
    private String toolchain;

    @Option( name = "-m", aliases = "--maven-repository", usage = "Maven remote repository from which load missing parent POMs." )
    private String remoteRepository;

    @Option( name = "-p", usage = "POM path pattern (glob)" )
    private String pomPattern = "**/*.pom,**/pom.xml";

    @Option( name = "-s", aliases = "--version-suffix", usage = "A suffix to append to each POM's version" )
    private String versionSuffix;

    @Option( name = "-P", aliases = { "--preserve" }, usage = "Write changed POMs back to original input files" )
    private boolean preserveFiles = false;

    @Option( name = "-C", aliases = "--config", usage = "Load default configuration for BOMs, toolchain, removedPluginsList, etc. from this file." )
    private final File config = new File( System.getProperty( "user.home" ), ".vman.properties" );

    @Option( name = "-W", aliases = { "--workspace" }, usage = "Backup original files here up before modifying." )
    private File workspace = new File( "vman-workspace" );

    @Option( name = "-R", aliases = { "--report-dir" }, usage = "Write reports here." )
    private File reports = new File( "vman-workspace/reports" );

    @Option( name = "-h", aliases = { "--help" }, usage = "Print this message and quit" )
    private boolean help = false;

    private static final Logger LOGGER = Logger.getLogger( Cli.class );

    public static final String REMOTE_REPOSITORY_PROPERTY = "remote-repository";

    public static final String VERSION_SUFFIX_PROPERTY = "version-suffix";

    public static final String TOOLCHAIN_PROPERTY = "toolchain";

    public static final String BOMS_LIST_PROPERTY = "boms";

    public static final String REMOVED_PLUGINS_PROPERTY = "removed-plugins";

    private static VersionManager vman;

    private List<String> boms;

    private List<String> removedPlugins;

    public static void main( final String[] args )
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
        catch ( MAEException e )
        {
            printUsage( parser, e );
        }
        catch ( MalformedURLException e )
        {
            printUsage( parser, e );
        }
    }

    public Cli( final File target, final File bomList )
    {
        this.target = target;
        this.bomList = bomList;
    }

    public Cli()
    {
    }

    public void run()
        throws MAEException, VManException, MalformedURLException
    {
        vman = VersionManager.getInstance();

        loadConfiguration();

        if ( boms == null && bomList != null )
        {
            loadBomList();
        }

        if ( removedPlugins == null && removedPluginsList != null )
        {
            loadRemovedPlugins();
        }

        final VersionManagerSession session =
            new VersionManagerSession( workspace, reports, versionSuffix, preserveFiles );

        if ( remoteRepository != null )
        {
            session.setRemoteRepository( remoteRepository );
        }

        if ( boms == null || boms.isEmpty() )
        {
            throw new VManException( "You must specify at least one BOM." );
        }

        if ( session.getErrors().isEmpty() )
        {
            LOGGER.info( "Modifying POM(s).\n\nTarget:\n\t" + target + "\n\nBOMs:\n\t"
                + StringUtils.join( boms.iterator(), "\n\t" ) + "\n\nWorkspace:\n\t" + workspace + "\n\nReports:\n\t"
                + reports );

            if ( target.isDirectory() )
            {
                vman.modifyVersions( target, pomPattern, boms, toolchain, removedPlugins, session );
            }
            else
            {
                vman.modifyVersions( target, boms, toolchain, removedPlugins, session );
            }
        }

        reports.mkdirs();
        vman.generateReports( reports, session );
    }

    private void loadRemovedPlugins()
    {
        if ( removedPluginsList != null )
        {
            String[] ls = removedPluginsList.split( "\\s*,\\s*" );
            removedPlugins = Arrays.asList( ls );
        }
    }

    private void loadConfiguration()
        throws VManException
    {
        if ( config != null && config.canRead() )
        {
            InputStream is = null;
            try
            {
                is = new FileInputStream( config );
                Properties props = new Properties();
                props.load( is );

                StringWriter sWriter = new StringWriter();
                props.list( new PrintWriter( sWriter ) );

                LOGGER.info( "Loading configuration from: " + config + ":\n\n" + sWriter );

                if ( removedPluginsList == null )
                {
                    removedPlugins = readListProperty( props, REMOVED_PLUGINS_PROPERTY );
                }

                if ( bomList == null )
                {
                    if ( boms == null )
                    {
                        boms = new ArrayList<String>();
                    }

                    List<String> pBoms = readListProperty( props, BOMS_LIST_PROPERTY );
                    if ( pBoms != null )
                    {
                        boms.addAll( pBoms );
                    }
                }

                if ( toolchain == null )
                {
                    toolchain = props.getProperty( TOOLCHAIN_PROPERTY );
                    if ( toolchain != null )
                    {
                        toolchain = toolchain.trim();
                    }
                }

                if ( versionSuffix == null )
                {
                    versionSuffix = props.getProperty( VERSION_SUFFIX_PROPERTY );
                    if ( versionSuffix != null )
                    {
                        versionSuffix = versionSuffix.trim();
                    }
                }

                if ( remoteRepository == null )
                {
                    remoteRepository = props.getProperty( REMOTE_REPOSITORY_PROPERTY );
                    if ( remoteRepository != null )
                    {
                        remoteRepository = remoteRepository.trim();
                    }
                }
            }
            catch ( IOException e )
            {
                throw new VManException( "Failed to load configuration from: " + config, e );
            }
            finally
            {
                closeQuietly( is );
            }
        }
    }

    private List<String> readListProperty( final Properties props, final String property )
    {
        String val = props.getProperty( property );
        if ( val != null )
        {
            String[] rm = val.split( "(\\s)*,(\\s)*" );
            return Arrays.asList( rm );
        }

        return null;
    }

    private void loadBomList()
        throws VManException
    {
        if ( boms == null )
        {
            boms = new ArrayList<String>();
        }

        if ( bomList != null && bomList.canRead() )
        {
            BufferedReader reader = null;
            try
            {
                reader = new BufferedReader( new FileReader( bomList ) );
                String line = null;
                while ( ( line = reader.readLine() ) != null )
                {
                    boms.add( line.trim() );
                }
            }
            catch ( final IOException e )
            {
                throw new VManException( "Failed to read bom list from: " + bomList, e );
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
    }

    private static void printUsage( final CmdLineParser parser, final Exception error )
    {
        if ( error != null )
        {
            System.err.println( "Invalid option(s): " + error.getMessage() );
            System.err.println();
        }

        parser.printUsage( System.err );
        System.err.println( "Usage: $0 [OPTIONS] [<target-path>]" );
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

    public File getTarget()
    {
        return target;
    }

    public void setTarget( final File target )
    {
        this.target = target;
    }

    public File getBomList()
    {
        return bomList;
    }

    public void setBomList( final File bomList )
    {
        this.bomList = bomList;
    }

    public void setRemovedPluginsList( final String removedPluginsList )
    {
        this.removedPluginsList = removedPluginsList;
    }

    public String getRemovedPluginsList()
    {
        return removedPluginsList;
    }

    public boolean isPreserveFiles()
    {
        return preserveFiles;
    }

    public void setPreserveFiles( final boolean preserveFiles )
    {
        this.preserveFiles = preserveFiles;
    }

    public File getWorkspace()
    {
        return workspace;
    }

    public void setWorkspace( final File workspace )
    {
        this.workspace = workspace;
    }

    public File getReports()
    {
        return reports;
    }

    public void setReports( final File reports )
    {
        this.reports = reports;
    }

    public String getToolchain()
    {
        return toolchain;
    }

    public void setToolchain( final String toolchain )
    {
        this.toolchain = toolchain;
    }

    public String getVersionSuffix()
    {
        return versionSuffix;
    }

    public void setVersionSuffix( final String versionSuffix )
    {
        this.versionSuffix = versionSuffix;
    }

}
