/*
 *  Copyright (C) 2012 Red Hat, Inc.
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

package com.redhat.rcm.version.util;

import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.io.IOUtils.copy;
import static org.apache.commons.lang.StringUtils.isEmpty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.log4j.Logger;

import com.redhat.rcm.version.VManException;

public final class InputUtils
{

    private static final Logger LOGGER = Logger.getLogger( InputUtils.class );

    private InputUtils()
    {
    }

    public static List<String> readListProperty( final Properties props, final String property )
    {
        final String val = props.getProperty( property );
        if ( val != null )
        {
            final String[] rm = val.split( "(\\s)*,(\\s)*" );
            return Arrays.asList( rm );
        }

        return null;
    }

    public static File readFileProperty( final Properties props, final String property, final File downloadsDir )
        throws VManException
    {
        return readFileProperty( props, property, downloadsDir, false );
    }

    public static File readFileProperty( final Properties props, final String property, final File downloadsDir,
                                         final boolean deleteExisting )
        throws VManException
    {
        final String val = props.getProperty( property );
        if ( val != null )
        {
            return getFile( val, downloadsDir, deleteExisting );
        }

        return null;
    }

    public static Map<String, String> readPropertiesList( final List<String> propertiesLocations,
                                                          final File downloadsDir, final boolean deleteExisting )
        throws VManException
    {
        if ( propertiesLocations == null )
        {
            return null;
        }

        final Map<String, String> result = new HashMap<String, String>();
        for ( final String propertiesLocation : propertiesLocations )
        {
            final File properties = getFile( propertiesLocation, downloadsDir, deleteExisting );
            String content;
            try
            {
                content = readFileToString( properties );
            }
            catch ( final IOException e )
            {
                throw new VManException( "Failed to load properties file: %s. Error: %s", e, properties, e.getMessage() );
            }

            final Map<String, String> props = parseProperties( content );
            for ( final Map.Entry<String, String> entry : props.entrySet() )
            {
                if ( !result.containsKey( entry.getKey() ) )
                {
                    result.put( entry.getKey(), entry.getValue() );
                }
            }
        }

        return result;
    }

    public static Map<String, String> readProperties( final String propertiesLocation, final File downloadsDir,
                                                      final boolean deleteExisting )
        throws VManException
    {
        final File properties = getFile( propertiesLocation, downloadsDir, deleteExisting );
        String content;
        try
        {
            content = readFileToString( properties );
        }
        catch ( final IOException e )
        {
            throw new VManException( "Failed to load properties file: %s. Error: %s", e, properties, e.getMessage() );
        }

        return parseProperties( content );
    }

    public static Map<String, String> readProperties( final File properties )
        throws VManException
    {
        String content;
        try
        {
            content = readFileToString( properties );
        }
        catch ( final IOException e )
        {
            throw new VManException( "Failed to load properties file: %s. Error: %s", e, properties, e.getMessage() );
        }

        return parseProperties( content );
    }

    public static URL getClasspathResource( final String resource )
    {
        return Thread.currentThread()
                     .getContextClassLoader()
                     .getResource( resource );
    }

    public static Map<String, String> readClasspathProperties( final String resource )
        throws VManException
    {
        final InputStream is = Thread.currentThread()
                                     .getContextClassLoader()
                                     .getResourceAsStream( resource );
        if ( is == null )
        {
            return null;
        }

        String content;
        try
        {
            content = IOUtils.toString( is );
        }
        catch ( final IOException e )
        {
            throw new VManException( "Failed to load properties resource: %s. Error: %s", e, resource, e.getMessage() );
        }
        finally
        {
            closeQuietly( is );
        }

        return parseProperties( content );
    }

    public static Map<String, String> parseProperties( final String content )
    {
        if ( content == null )
        {
            return null;
        }

        final Map<String, String> properties = new LinkedHashMap<String, String>();

        final String[] lines = content.split( "\\s*[,\\n]\\s*" );
        if ( lines != null && lines.length > 0 )
        {
            // int count = 0;
            for ( final String line : lines )
            {
                // LOGGER.info( "processing: '" + line + "'" );

                String ln = line.trim();
                if ( ln.startsWith( "#" ) )
                {
                    continue;
                }

                final int idx = ln.indexOf( '#' );
                if ( idx > -1 )
                {
                    ln = line.substring( 0, idx );
                }

                final String[] kv = ln.split( "\\s*=\\s*" );
                if ( kv.length < 2 || kv[0].length() < 1 || kv[1].length() < 1 )
                {
                    LOGGER.warn( "Invalid property; key and value must not be empty! (line: '" + line + "')" );
                    continue;
                }

                properties.put( propCleanup( kv[0] ), propCleanup( kv[1] ) );
                // count++;
            }

            // LOGGER.info( "Found " + count + " properties..." );
        }

        return properties;
    }

    private static String propCleanup( final String value )
    {
        return value.replace( "\\:", ":" )
                    .replace( "\\=", "=" )
                    .trim();
    }

    public static File[] getFiles( final List<String> locations, final File downloadsDir )
        throws VManException
    {
        return getFiles( locations, downloadsDir, false );
    }

    public static File[] getFiles( final List<String> locations, final File downloadsDir, final boolean deleteExisting )
        throws VManException
    {
        final List<File> result = new ArrayList<File>( locations.size() );

        for ( final String bom : locations )
        {
            final File bomFile = getFile( bom, downloadsDir, deleteExisting );
            if ( bomFile != null )
            {
                result.add( bomFile );
            }
        }

        return result.toArray( new File[] {} );
    }

    private static DefaultHttpClient client;

    public static File getFile( final String location, final File downloadsDir )
        throws VManException
    {
        return getFile( location, downloadsDir, false );
    }

    public static File getFile( final String location, final File downloadsDir, final boolean deleteExisting )
        throws VManException
    {
        if ( client == null )
        {
            final DefaultHttpClient hc = new DefaultHttpClient();
            hc.setRedirectStrategy( new DefaultRedirectStrategy() );

            final String proxyHost = System.getProperty( "http.proxyHost" );
            final int proxyPort = Integer.parseInt( System.getProperty( "http.proxyPort", "-1" ) );

            if ( proxyHost != null && proxyPort > 0 )
            {
                final HttpHost proxy = new HttpHost( proxyHost, proxyPort );
                hc.getParams()
                  .setParameter( ConnRouteParams.DEFAULT_PROXY, proxy );
            }

            client = hc;
        }

        File result = null;

        if ( location.startsWith( "http" ) )
        {
            LOGGER.info( "Downloading: '" + location + "'..." );

            try
            {
                final URL url = new URL( location );
                final String userpass = url.getUserInfo();
                if ( !isEmpty( userpass ) )
                {
                    final AuthScope scope = new AuthScope( url.getHost(), url.getPort() );
                    final Credentials creds = new UsernamePasswordCredentials( userpass );

                    client.getCredentialsProvider()
                          .setCredentials( scope, creds );
                }
            }
            catch ( final MalformedURLException e )
            {
                LOGGER.error( "Malformed URL: '" + location + "'", e );
                throw new VManException( "Failed to download: %s. Reason: %s", e, location, e.getMessage() );
            }

            final File downloaded = new File( downloadsDir, new File( location ).getName() );
            if ( deleteExisting && downloaded.exists() )
            {
                downloaded.delete();
            }

            if ( !downloaded.exists() )
            {
                HttpGet get = new HttpGet( location );
                OutputStream out = null;
                try
                {
                    HttpResponse response = client.execute( get );
                    // Work around for scenario where we are loading from a server
                    // that does a refresh e.g. gitweb
                    if (response.containsHeader( "Cache-control" ))
                    {
                        LOGGER.info( "Waiting for server to generate cache..." );
                        try
                        {
                            Thread.sleep (5000);
                        }
                        catch ( InterruptedException e )
                        {
                        }
                        get.abort();
                        get = new HttpGet( location );
                        response = client.execute( get );
                    }
                    final int code = response.getStatusLine()
                                             .getStatusCode();
                    if ( code == 200 )
                    {
                        final InputStream in = response.getEntity()
                                                       .getContent();
                        out = new FileOutputStream( downloaded );

                        copy( in, out );
                    }
                    else
                    {
                        LOGGER.info( String.format( "Received status: '%s' while downloading: %s",
                                                    response.getStatusLine(), location ) );

                        throw new VManException( "Received status: '%s' while downloading: %s",
                                                 response.getStatusLine(), location );
                    }
                }
                catch ( final ClientProtocolException e )
                {
                    throw new VManException( "Failed to download: '%s'. Error: %s", e, location, e.getMessage() );
                }
                catch ( final IOException e )
                {
                    throw new VManException( "Failed to download: '%s'. Error: %s", e, location, e.getMessage() );
                }
                finally
                {
                    closeQuietly( out );
                    get.abort();
                }
            }

            result = downloaded;
        }
        else
        {
            LOGGER.info( "Using local file: '" + location + "'..." );

            result = new File( location );
        }

        return result;
    }

}
