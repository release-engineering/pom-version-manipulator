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
import static org.apache.commons.lang.StringUtils.join;

import org.apache.log4j.Logger;
import org.apache.maven.mae.MAEException;
import org.codehaus.plexus.util.StringUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ExampleMode;
import org.kohsuke.args4j.Option;

import com.redhat.rcm.version.mgr.VersionManager;
import com.redhat.rcm.version.mgr.mod.ProjectModder;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class Cli
{
    @Argument( index = 0, metaVar = "target", usage = "POM file (or directory containing POM files) to modify." )
    private File target = new File( System.getProperty( "user.dir" ), "pom.xml" );

    @Option( name = "-r", aliases = { "--rm-plugins", "--removed-plugins" }, usage = "List of plugins (format: <groupId:artifactId>[,<groupId:artifactId>]) to REMOVE if found" )
    private String removedPluginsList;

    @Option( name = "-b", aliases = "--boms", usage = "File containing a list of BOM URLs to use for standardizing dependencies" )
    private File bomList;

    @Option( name = "-t", aliases = "--toolchain", usage = "Toolchain POM URL, containing standard plugin versions in the build/pluginManagement section, "
        + "and plugin injections in the regular build/plugins section." )
    private String toolchain;

    @Option( name = "-m", aliases = "--remote-repository", usage = "Maven remote repository from which load missing parent POMs." )
    private String remoteRepository;

    @Option( name = "-p", usage = "POM path pattern (glob)" )
    private String pomPattern = "**/*.pom,**/pom.xml";

    @Option( name = "-e", usage = "POM exclude path pattern (glob)" )
    private String pomExcludePattern;

    @Option( name = "-E", usage = "POM exclude module list (groupId:artifactId,groupId:artifactId...)" )
    private String pomExcludeModules;

    @Option( name = "-s", aliases = "--version-suffix", usage = "A suffix to append to each POM's version" )
    private String versionSuffix;

    @Option( name = "--strict", usage = "Change ONLY the dependencies, plugins, and parents that are listed in BOMs and toolchain POM" )
    private boolean strict = false;

    @Option( name = "--dont-inject-boms", usage = "Do NOT inject BOMs directly into root project file(s)" )
    private boolean injectBoms = true;

    @Option( name = "-P", aliases = { "--preserve" }, usage = "Write changed POMs back to original input files" )
    private boolean preserveFiles = false;

    @Option( name = "-C", aliases = "--config", usage = "Load default configuration for BOMs, toolchain, removedPluginsList, etc. from this file." )
    private File config = new File( System.getProperty( "user.home" ), ".vman.properties" );

    @Option( name = "-W", aliases = { "--workspace" }, usage = "Backup original files here up before modifying." )
    private File workspace = new File( "vman-workspace" );

    @Option( name = "-R", aliases = { "--report-dir" }, usage = "Write reports here." )
    private File reports = new File( "vman-workspace/reports" );

    @Option( name = "-h", aliases = { "--help" }, usage = "Print this message and quit" )
    private boolean help = false;

    @Option( name = "-H", aliases = { "--help-modifications" }, usage = "Print the list of available modifications and quit" )
    private boolean helpModders = false;

    @Option( name = "-S", aliases = { "--settings" }, usage = "Maven settings.xml file." )
    private File settings;

    @Option( name = "-L", aliases = { "--local-repo", "--local-repository" }, usage = "Local repository directory." )
    private File localRepository;

    @Option( name = "-Z", aliases = { "--no-system-exit" }, usage = "Don't call System.exit(..) with the return value (for embedding/testing)." )
    private boolean noSystemExit;

    @Option( name = "-O", aliases = { "--capture-output", "--capture-pom" }, usage = "Write captured (missing) definitions to this POM location." )
    private File capturePom;

    @Option( name = "-M", aliases = { "--enable-modifications" }, usage = "List of modifications to enable for this execution (see --help-modifications for more information)." )
    private String modifications;

    private static final Logger LOGGER = Logger.getLogger( Cli.class );

    public static final String REMOTE_REPOSITORY_PROPERTY = "remote-repository";

    public static final String VERSION_SUFFIX_PROPERTY = "version-suffix";

    public static final String TOOLCHAIN_PROPERTY = "toolchain";

    public static final String BOMS_LIST_PROPERTY = "boms";

    public static final String REMOVED_PLUGINS_PROPERTY = "removed-plugins";

    public static final String LOCAL_REPOSITORY_PROPERTY = "local-repository";

    public static final String SETTINGS_PROPERTY = "settings";

    public static final String CAPTURE_POM_PROPERTY = "capture-pom";

    public static final String STRICT_MODE_PROPERTY = "strict";

    public static final String INJECT_BOMS_PROPERTY = "inject-boms";

    public static final String MODIFICATIONS = "modifications";

    private static VersionManager vman;

    private List<String> boms;

    private List<String> removedPlugins;

    private Set<String> modders;

    private static int exitValue = Integer.MIN_VALUE;

    public static int exitValue()
    {
        return exitValue;
    }

    public static void main( final String[] args )
    {
        final Cli cli = new Cli();
        final CmdLineParser parser = new CmdLineParser( cli );
        try
        {
            parser.parseArgument( args );

            vman = VersionManager.getInstance();

            exitValue = 0;
            if ( cli.help )
            {
                printUsage( parser, null );
            }
            else if ( cli.helpModders )
            {
                printModders();
            }
            else
            {
                exitValue = cli.run();
            }

            if ( !cli.noSystemExit )
            {
                System.exit( exitValue );
            }
        }
        catch ( final CmdLineException error )
        {
            printUsage( parser, error );
        }
        catch ( final MAEException e )
        {
            printUsage( parser, e );
        }
        catch ( final MalformedURLException e )
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

    public int run()
        throws MAEException, VManException, MalformedURLException
    {
        loadConfiguration();

        if ( boms == null && bomList != null )
        {
            loadBomList();
        }

        if ( removedPlugins == null && removedPluginsList != null )
        {
            loadRemovedPlugins();
        }

        loadAndNormalizeModifications();

        printConfig();

        final VersionManagerSession session =
            new VersionManagerSession( workspace,
                                       reports,
                                       versionSuffix,
                                       removedPlugins,
                                       modders,
                                       preserveFiles,
                                       strict,
                                       injectBoms );

        if ( remoteRepository != null )
        {
            session.setRemoteRepository( remoteRepository );
        }

        if ( settings != null )
        {
            session.setSettingsXml( settings );
        }

        if ( localRepository == null )
        {
            localRepository = new File( workspace, "local-repository" );
        }

        session.setLocalRepositoryDirectory( localRepository );

        if ( capturePom != null )
        {
            session.setCapturePom( capturePom );
        }

        if ( boms == null || boms.isEmpty() )
        {
            LOGGER.error( "You must specify at least one BOM." );
            return -2;
        }

        if ( session.getErrors().isEmpty() )
        {
            LOGGER.info( "Modifying POM(s).\n\nTarget:\n\t" + target + "\n\nBOMs:\n\t"
                + StringUtils.join( boms.iterator(), "\n\t" ) + "\n\nWorkspace:\n\t" + workspace + "\n\nReports:\n\t"
                + reports );

            vman.setPomExcludeModules (pomExcludeModules);
            
            if ( target.isDirectory() )
            {
                vman.modifyVersions( target, pomPattern, pomExcludePattern, boms, toolchain, session );
            }
            else
            {
                vman.modifyVersions( target, boms, toolchain, session );
            }
        }

        reports.mkdirs();
        vman.generateReports( reports, session );

        if ( capturePom != null && capturePom.exists() )
        {
            LOGGER.warn( "\n\n\n\n\nMissing dependency/plugin information has been captured in:\n\n\t"
                + capturePom.getAbsolutePath() + "\n\n\n\n" );

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

    private void printConfig()
    {
        LOGGER.info( "modifications = " + join( modders, " " ) );
    }

    private static void printModders()
    {
        final Map<String, ProjectModder> modders = vman.getModders();
        final List<String> keys = new ArrayList<String>( modders.keySet() );
        Collections.sort( keys );

        int max = 0;
        for ( final String key : keys )
        {
            max = Math.max( max, key.length() );
        }

        final StringBuilder sb = new StringBuilder();
        sb.append( "The following project modifications are available: " );

        final int descMax = 75 - max;
        final String fmt = "%-" + max + "s    %-" + descMax + "s\n";

        final List<String> lines = new ArrayList<String>();
        for ( final String key : keys )
        {
            final String description = modders.get( key ).getDescription();
            lines.clear();
            final BreakIterator iter = BreakIterator.getLineInstance();
            iter.setText( description );

            int start = iter.first();
            int end = BreakIterator.DONE;
            final StringBuilder currentLine = new StringBuilder();
            String seg;
            while ( start != BreakIterator.DONE && ( end = iter.next() ) != BreakIterator.DONE )
            {
                seg = description.substring( start, end );
                if ( currentLine.length() + seg.length() > descMax )
                {
                    lines.add( currentLine.toString() );
                    currentLine.setLength( 0 );
                }

                currentLine.append( seg );
                start = end;
            }

            if ( currentLine.length() > 0 )
            {
                lines.add( currentLine.toString() );
            }

            System.out.printf( fmt, key, lines.get( 0 ) );
            if ( lines.size() > 1 )
            {
                for ( int i = 1; i < lines.size(); i++ )
                {
                    System.out.printf( fmt, "", lines.get( i ) );
                }
            }

            System.out.println();
        }

        System.out.println( "\n\nNOTE: To ADD any of these modifiers to the standard list, use the notation '--modifications=+<modifier-id>' (prefixed with '+').\n\nThe standard modifiers are: " );
        for ( final String key : ProjectModder.STANDARD_MODIFICATIONS )
        {
            System.out.printf( "\n  - %s", key );
        }
        System.out.println();
        System.out.println();
    }

    private void loadRemovedPlugins()
    {
        if ( removedPluginsList != null )
        {
            final String[] ls = removedPluginsList.split( "\\s*,\\s*" );
            removedPlugins = Arrays.asList( ls );
        }
    }

    private void loadAndNormalizeModifications()
    {
        if ( modifications != null )
        {
            final String[] ls = modifications.split( "\\s*,\\s*" );
            modders = new HashSet<String>( Arrays.asList( ls ) );
        }

        final Set<String> mods = new HashSet<String>();
        boolean loadStandards = modders == null;
        if ( modders != null )
        {
            if ( !modders.isEmpty() && modders.iterator().next().startsWith( "+" ) )
            {
                loadStandards = true;
            }

            for ( final String key : modders )
            {
                if ( ProjectModder.STANDARD_MODS_ALIAS.equals( key ) )
                {
                    loadStandards = true;
                }
                else if ( key.startsWith( "+" ) )
                {
                    if ( key.length() > 1 )
                    {
                        mods.add( key.substring( 1 ).trim() );
                    }
                }
                else
                {
                    mods.add( key );
                }
            }
        }

        if ( loadStandards )
        {
            mods.addAll( Arrays.asList( ProjectModder.STANDARD_MODIFICATIONS ) );
        }

        modders = mods;

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
                final Properties props = new Properties();
                props.load( is );

                final StringWriter sWriter = new StringWriter();
                props.list( new PrintWriter( sWriter ) );

                LOGGER.info( "Loading configuration from: " + config + ":\n\n" + sWriter );

                if ( removedPluginsList == null )
                {
                    removedPlugins = readListProperty( props, REMOVED_PLUGINS_PROPERTY );
                }

                if ( modifications == null )
                {
                    final List<String> lst = readListProperty( props, MODIFICATIONS );
                    LOGGER.info( "modifications from properties: '" + join( lst, " " ) + "'" );
                    if ( lst != null )
                    {
                        modders = modders == null ? new HashSet<String>() : new HashSet<String>( modders );
                        modders.addAll( lst );
                    }
                }

                if ( bomList == null )
                {
                    if ( boms == null )
                    {
                        boms = new ArrayList<String>();
                    }

                    final List<String> pBoms = readListProperty( props, BOMS_LIST_PROPERTY );
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

                if ( settings == null )
                {
                    final String s = props.getProperty( SETTINGS_PROPERTY );
                    if ( s != null )
                    {
                        settings = new File( s );
                    }
                }

                if ( localRepository == null )
                {
                    final String l = props.getProperty( LOCAL_REPOSITORY_PROPERTY );
                    if ( l != null )
                    {
                        localRepository = new File( l );
                    }
                }

                if ( capturePom == null )
                {
                    final String p = props.getProperty( CAPTURE_POM_PROPERTY );
                    if ( p != null )
                    {
                        capturePom = new File( p );
                    }
                }

                if ( !strict )
                {
                    strict =
                        Boolean.valueOf( props.getProperty( STRICT_MODE_PROPERTY, Boolean.toString( Boolean.FALSE ) ) );
                }

                if ( injectBoms )
                {
                    injectBoms =
                        Boolean.valueOf( props.getProperty( INJECT_BOMS_PROPERTY, Boolean.toString( Boolean.TRUE ) ) );
                }
            }
            catch ( final IOException e )
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
        final String val = props.getProperty( property );
        if ( val != null )
        {
            final String[] rm = val.split( "(\\s)*,(\\s)*" );
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

    protected String getPomExcludePattern()
    {
        return pomExcludePattern;
    }

    protected void setPomExcludePattern( final String pomExcludePattern )
    {
        this.pomExcludePattern = pomExcludePattern;
    }

    protected String getPomExcludeModules()
    {
        return pomExcludeModules;
    }

    protected void setPomExcludeModules( final String pomExcludeModules )
    {
        this.pomExcludeModules = pomExcludeModules;
    }

    protected boolean isHelp()
    {
        return help;
    }

    protected void setHelp( final boolean help )
    {
        this.help = help;
    }

    protected boolean isHelpModifications()
    {
        return help;
    }

    protected void setHelpModifications( final boolean help )
    {
        helpModders = help;
    }

    protected String getModifications()
    {
        return modifications;
    }

    protected void setModifications( final String modifications )
    {
        this.modifications = modifications;
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

    protected File getConfig()
    {
        return config;
    }

    protected void setConfig( final File config )
    {
        this.config = config;
    }

}
