/*
 *  Copyright (C) 2012 John Casey.
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

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.io.IOUtils.copy;
import static org.apache.commons.lang.StringUtils.isEmpty;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.log4j.Logger;

import com.redhat.rcm.version.VManException;

import java.io.File;
import java.io.FileInputStream;
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
        final String val = props.getProperty( property );
        if ( val != null )
        {
            return getFile( val, downloadsDir );
        }

        return null;
    }

    public static Map<String, String> readProperties( final File properties )
        throws VManException
    {
        final Properties props = new Properties();
        InputStream is = null;
        try
        {
            is = new FileInputStream( properties );
            props.load( is );
        }
        catch ( final IOException e )
        {
            throw new VManException( "Failed to load properties file: %s. Error: %s", e, properties, e.getMessage() );
        }
        finally
        {
            closeQuietly( is );
        }

        final Map<String, String> result = new HashMap<String, String>( props.size() );
        for ( final String key : props.stringPropertyNames() )
        {
            result.put( key, props.getProperty( key ) );
        }

        return result;
    }

    public static Map<String, String> parseProperties( final String content )
    {
        if ( content == null )
        {
            return null;
        }

        final Map<String, String> properties = new LinkedHashMap<String, String>();

        final String[] lines = content.split( "\\s*,\\s*" );
        if ( lines != null && lines.length > 0 )
        {
            int count = 0;
            for ( final String line : lines )
            {
                LOGGER.info( "processing: '" + line + "'" );

                String ln = line;
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

                properties.put( kv[0], kv[1] );
                count++;
            }

            LOGGER.info( "Found " + count + " properties..." );
        }

        return properties;
    }

    public static File[] getFiles( final List<String> boms, final File downloadsDir )
        throws VManException
    {
        final List<File> result = new ArrayList<File>( boms.size() );

        for ( final String bom : boms )
        {
            final File bomFile = getFile( bom, downloadsDir );
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
        if ( client == null )
        {
            client = new DefaultHttpClient();
            client.setRedirectStrategy( new DefaultRedirectStrategy() );
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

                    client.getCredentialsProvider().setCredentials( scope, creds );
                }
            }
            catch ( final MalformedURLException e )
            {
                LOGGER.error( "Malformed URL: '" + location + "'", e );
                throw new VManException( "Failed to download: %s. Reason: %s", e, location, e.getMessage() );
            }

            final File downloaded = new File( downloadsDir, new File( location ).getName() );
            if ( !downloaded.exists() )
            {
                final HttpGet get = new HttpGet( location );
                OutputStream out = null;
                try
                {
                    final HttpResponse response = client.execute( get );
                    final int code = response.getStatusLine().getStatusCode();
                    if ( code == 200 )
                    {
                        final InputStream in = response.getEntity().getContent();
                        out = new FileOutputStream( downloaded );

                        copy( in, out );
                    }
                    else
                    {
                        LOGGER.info( String.format( "Received status: '%s' while downloading: %s",
                                                    response.getStatusLine(),
                                                    location ) );

                        throw new VManException( "Received status: '%s' while downloading: %s",
                                                 response.getStatusLine(),
                                                 location );
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
            result = new File( location );
        }

        return result;
    }

}
