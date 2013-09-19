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
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
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
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.codehaus.plexus.util.DirectoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

public final class InputUtils
{
    private static final Logger logger = LoggerFactory.getLogger( InputUtils.class );

    private static final int MAX_RETRIES = 5;

    private InputUtils()
    {
    }

    public static String[] getIncludedSubpaths( final File basedir, final String includes, final String excludes, final VersionManagerSession session )
    {
        final DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( basedir );

        final String[] initExcludes = new String[] { session.getWorkspace()
                                                            .getName() + "/**", session.getReports()
                                                                                       .getName() + "/**" };

        final String[] excludePattern = excludes == null ? new String[] {} : excludes.split( "\\s*,\\s*" );

        final String[] excluded = Arrays.copyOf( initExcludes, initExcludes.length + excludePattern.length );

        System.arraycopy( excludePattern, 0, excluded, initExcludes.length, excludePattern.length );

        scanner.setExcludes( excluded );
        scanner.addDefaultExcludes();

        final String[] included = includes == null ? new String[] { "**/pom.xml", "**/*.pom" } : includes.split( "\\s*,\\s*" );

        scanner.setIncludes( included );

        scanner.scan();

        final String[] includedSubpaths = scanner.getIncludedFiles();

        logger.info( "Scanning from " + basedir + " and got included files " + Arrays.toString( includedSubpaths ) + " and got excluded files "
            + Arrays.toString( scanner.getExcludedFiles() ) );

        return includedSubpaths;
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

    public static File readFileProperty( final Properties props, final String property, final File downloadsDir, final boolean deleteExisting )
        throws VManException
    {
        final String val = props.getProperty( property );
        if ( val != null )
        {
            return getFile( val, downloadsDir, deleteExisting );
        }

        return null;
    }

    public static Map<String, String> readPropertiesList( final List<String> propertiesLocations, final File downloadsDir,
                                                          final boolean deleteExisting )
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

    public static Map<String, String> readProperties( final String propertiesLocation, final File downloadsDir, final boolean deleteExisting )
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
                // logger.info( "processing: '" + line + "'" );

                String ln = line.trim();
                if ( ln.startsWith( "#" ) )
                {
                    continue;
                }

                int idx = ln.indexOf( '#' );
                if ( idx > -1 )
                {
                    ln = line.substring( 0, idx );
                }

                idx = ln.indexOf( "=" );
                if ( idx < 0 )
                {
                    logger.warn( "Invalid property; key and value must not be empty! (line: '" + line + "')" );
                    continue;
                }
                final String k = propCleanup( ln.substring( 0, idx )
                                                .trim() );
                final String v = propCleanup( ln.substring( idx + 1 )
                                                .trim() );

                properties.put( k, v );

                // count++;
            }

            // logger.info( "Found " + count + " properties..." );
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

    private static KeyStore trustKs;

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
            setupClient();
        }

        File result = null;

        if ( location.startsWith( "http" ) )
        {
            logger.info( "Downloading: '" + location + "'..." );

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
                logger.error( "Malformed URL: '" + location + "'", e );
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
                    HttpResponse response = null;
                    // Work around for scenario where we are loading from a server
                    // that does a refresh e.g. gitweb
                    final int tries = 0;
                    do
                    {
                        get = new HttpGet( location );
                        response = client.execute( get );
                        if ( response.containsHeader( "Cache-control" ) )
                        {
                            logger.info( "Waiting for server to generate cache..." );
                            get.abort();
                            try
                            {
                                Thread.sleep( 3000 );
                            }
                            catch ( final InterruptedException e )
                            {
                            }
                        }
                        else
                        {
                            break;
                        }
                    }
                    while ( tries < MAX_RETRIES );

                    if ( response.containsHeader( "Cache-control" ) )
                    {
                        throw new VManException( "Failed to read: %s. Cache-control header was present in final attempt.", location );
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
                        logger.info( "Received status: '{}' while downloading: {}", response.getStatusLine(), location );

                        throw new VManException( "Received status: '%s' while downloading: %s", response.getStatusLine(), location );
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
            logger.info( "Using local file: '" + location + "'..." );

            result = new File( location );
        }

        return result;
    }

    private static void setupClient()
        throws VManException
    {
        if ( client == null )
        {
            SSLSocketFactory sslSocketFactory;
            try
            {
                sslSocketFactory =
                    new SSLSocketFactory( SSLSocketFactory.TLS, null, null, trustKs, null, new TrustSelfSignedStrategy(),
                                          SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER );
                //                sslSocketFactory =
                //                    new SSLSocketFactory( SSLSocketFactory.TLS, null, null, trustKs, null, null,
                //                                          SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER );
            }
            catch ( final KeyManagementException e )
            {
                logger.error( "Failed to setup SSL socket factory: {}", e, e.getMessage() );
                throw new VManException( "Failed to setup SSL socket factory: %s", e, e.getMessage() );
            }
            catch ( final UnrecoverableKeyException e )
            {
                logger.error( "Failed to setup SSL socket factory: {}", e, e.getMessage() );
                throw new VManException( "Failed to setup SSL socket factory: %s", e, e.getMessage() );
            }
            catch ( final NoSuchAlgorithmException e )
            {
                logger.error( "Failed to setup SSL socket factory: {}", e, e.getMessage() );
                throw new VManException( "Failed to setup SSL socket factory: %s", e, e.getMessage() );
            }
            catch ( final KeyStoreException e )
            {
                logger.error( "Failed to setup SSL socket factory: {}", e, e.getMessage() );
                throw new VManException( "Failed to setup SSL socket factory: %s", e, e.getMessage() );
            }

            final ThreadSafeClientConnManager ccm = new ThreadSafeClientConnManager();
            ccm.getSchemeRegistry()
               .register( new Scheme( "https", 443, sslSocketFactory ) );

            final DefaultHttpClient hc = new DefaultHttpClient( ccm );
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
    }

    public static void setTrustKeyStore( final KeyStore trustKs )
    {
        InputUtils.trustKs = trustKs;
    }

}
