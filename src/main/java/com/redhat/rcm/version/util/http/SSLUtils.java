package com.redhat.rcm.version.util.http;

import static org.apache.commons.codec.binary.Base64.decodeBase64;
import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.util.InputUtils;

public final class SSLUtils
{

    private static final String CLASSPATH_PREFIX = "classpath:";

    private SSLUtils()
    {
    }

    public static void initSSLContext( final String basedir )
        throws VManException
    {
        KeyManagerFactory kmf;
        try
        {
            kmf = KeyManagerFactory.getInstance( KeyManagerFactory.getDefaultAlgorithm() );
            kmf.init( null, null );
        }
        catch ( final NoSuchAlgorithmException e )
        {
            throw new VManException( "Cannot initialize KeyManagerFactory: %s", e, e.getMessage() );
        }
        catch ( final UnrecoverableKeyException e )
        {
            throw new VManException( "Cannot initialize KeyManagerFactory: %s", e, e.getMessage() );
        }
        catch ( final KeyStoreException e )
        {
            throw new VManException( "Cannot initialize KeyManagerFactory: %s", e, e.getMessage() );
        }

        KeyManager km = null;
        for ( final KeyManager keyManager : kmf.getKeyManagers() )
        {
            if ( keyManager instanceof X509KeyManager )
            {
                km = keyManager;
            }
        }

        final TrustManager tm = loadTrustManager( basedir );

        SSLContext ctx;
        try
        {
            ctx = SSLContext.getInstance( "SSL" );
        }
        catch ( final NoSuchAlgorithmException e )
        {
            throw new VManException( "Failed to retrieve SSLContext: %s", e, e.getMessage() );
        }

        try
        {
            ctx.init( new KeyManager[] { km }, new TrustManager[] { tm }, null );
        }
        catch ( final KeyManagementException e )
        {
            throw new VManException( "Failed to initialize SSLContext with new PEM-based TrustStore: %s", e,
                                     e.getMessage() );
        }

        SSLContext.setDefault( ctx );
    }

    private static TrustManager loadTrustManager( final String basedir )
        throws VManException
    {
        KeyStore ks;
        try
        {
            ks = KeyStore.getInstance( KeyStore.getDefaultType() );
            ks.load( null );
        }
        catch ( final KeyStoreException e )
        {
            throw new VManException( "Failed to load trust-store KeyStore instance: %s", e, e.getMessage() );
        }
        catch ( final NoSuchAlgorithmException e )
        {
            throw new VManException( "Failed to load trust-store KeyStore instance: %s", e, e.getMessage() );
        }
        catch ( final CertificateException e )
        {
            throw new VManException( "Failed to load trust-store KeyStore instance: %s", e, e.getMessage() );
        }
        catch ( final IOException e )
        {
            throw new VManException( "Failed to load trust-store KeyStore instance: %s", e, e.getMessage() );
        }

        if ( basedir.startsWith( CLASSPATH_PREFIX ) )
        {
            final String cpDir = basedir.substring( CLASSPATH_PREFIX.length() );
            loadFromClasspath( cpDir, ks );
        }
        else
        {
            final File dir = new File( basedir );
            if ( dir.exists() && dir.isDirectory() )
            {
                final String[] fnames = dir.list();
                if ( fnames != null )
                {
                    for ( final String fname : fnames )
                    {
                        final File f = new File( dir, fname );
                        loadFromFile( f.getPath(), ks );
                    }
                }
            }
        }

        InputUtils.setTrustKeyStore( ks );

        TrustManagerFactory dtmf;
        try
        {
            dtmf = TrustManagerFactory.getInstance( TrustManagerFactory.getDefaultAlgorithm() );
            dtmf.init( (KeyStore) null );
        }
        catch ( final NoSuchAlgorithmException e )
        {
            throw new VManException( "Failed to initialize default trust-store: %s", e, e.getMessage() );
        }
        catch ( final KeyStoreException e )
        {
            throw new VManException( "Failed to initialize default trust-store: %s", e, e.getMessage() );
        }

        X509TrustManager dtm = null;
        for ( final TrustManager ctm : dtmf.getTrustManagers() )
        {
            if ( ctm instanceof X509TrustManager )
            {
                dtm = (X509TrustManager) ctm;
                break;
            }
        }

        try
        {
            if ( ks.size() < 1 )
            {
                return dtm;
            }
        }
        catch ( final KeyStoreException e )
        {
        }

        TrustManagerFactory tmf;
        try
        {
            tmf = TrustManagerFactory.getInstance( TrustManagerFactory.getDefaultAlgorithm() );
            tmf.init( ks );
        }
        catch ( final NoSuchAlgorithmException e )
        {
            throw new VManException( "Failed to initialize trust-store from .pem files: %s", e, e.getMessage() );
        }
        catch ( final KeyStoreException e )
        {
            throw new VManException( "Failed to initialize trust-store from .pem files: %s", e, e.getMessage() );
        }

        X509TrustManager tm = null;
        for ( final TrustManager ctm : tmf.getTrustManagers() )
        {
            if ( ctm instanceof X509TrustManager )
            {
                tm = (X509TrustManager) ctm;
                break;
            }
        }

        return new MultiTrustManager( tm, dtm );
    }

    private static void loadFromClasspath( final String basepath, final KeyStore ks )
        throws VManException
    {
        Enumeration<URL> resources;
        try
        {
            resources = Thread.currentThread()
                              .getContextClassLoader()
                              .getResources( basepath );
        }
        catch ( final IOException e )
        {
            throw new VManException( "Failed to scan classpath for certificate base path: %s. Reason: %s", e, basepath,
                                     e.getMessage() );
        }

        final List<URL> urls = Collections.list( resources );
        for ( final URL url : urls )
        {
            if ( "jar".equals( url.getProtocol() ) )
            {
                loadFromJar( url, basepath, ks );
            }
            else
            {
                loadFromFile( url.getPath(), ks );
            }
        }
    }

    private static void loadFromFile( final String path, final KeyStore ks )
        throws VManException
    {
        final File f = new File( path );
        if ( f.exists() && f.isFile() )
        {
            InputStream is = null;
            try
            {
                is = new FileInputStream( f );
                readCerts( is, f.getName(), ks );
            }
            catch ( final CertificateException e )
            {
                throw new VManException( "Failed to read classpath certificate file: %s. Reason: %s", e, f,
                                         e.getMessage() );
            }
            catch ( final KeyStoreException e )
            {
                throw new VManException( "Failed to add certificate from classpath file: %s. Reason: %s", e, f,
                                         e.getMessage() );
            }
            catch ( final NoSuchAlgorithmException e )
            {
                throw new VManException( "Failed to read classpath certificate file: %s. Reason: %s", e, f,
                                         e.getMessage() );
            }
            catch ( final IOException e )
            {
                throw new VManException( "Failed to read classpath certificate file: %s. Reason: %s", e, f,
                                         e.getMessage() );
            }
            finally
            {
                if ( is != null )
                {
                    try
                    {
                        is.close();
                    }
                    catch ( final IOException e )
                    {
                    }
                }
            }
        }
    }

    private static void loadFromJar( final URL url, final String basepath, final KeyStore ks )
        throws VManException
    {
        String jar = url.getPath();
        final int idx = jar.indexOf( "!" );
        if ( idx > -1 )
        {
            jar = jar.substring( 0, idx );
        }

        if ( jar.startsWith( "file:" ) )
        {
            jar = jar.substring( 5 );
        }

        try
        {
            final JarFile jf = new JarFile( jar );

            final List<JarEntry> entries = Collections.list( jf.entries() );
            for ( final JarEntry entry : entries )
            {
                final String name = entry.getName();
                if ( name.startsWith( basepath ) )
                {
                    final InputStream is = jf.getInputStream( entry );
                    try
                    {
                        readCerts( is, new File( name ).getName(), ks );
                    }
                    catch ( final CertificateException e )
                    {
                        throw new VManException(
                                                 "Failed to read certificates from classpath jar entry: %s!%s. Reason: %s",
                                                 e, jar, name, e.getMessage() );
                    }
                    catch ( final KeyStoreException e )
                    {
                        throw new VManException(
                                                 "Failed to read certificates from classpath jar entry: %s!%s. Reason: %s",
                                                 e, jar, name, e.getMessage() );
                    }
                    catch ( final NoSuchAlgorithmException e )
                    {
                        throw new VManException(
                                                 "Failed to read certificates from classpath jar entry: %s!%s. Reason: %s",
                                                 e, jar, name, e.getMessage() );
                    }
                    finally
                    {
                        if ( is != null )
                        {
                            try
                            {
                                is.close();
                            }
                            catch ( final IOException eInner )
                            {
                            }
                        }
                    }
                }
            }
        }
        catch ( final IOException e )
        {
            throw new VManException( "Failed to open classpath jar: %s. Reason: %s", e, jar, e.getMessage() );
        }
    }

    public static void readKeyAndCert( final InputStream is, final String keyPass, final KeyStore ks )
        throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, InvalidKeySpecException
    {
        final CertificateFactory certFactory = CertificateFactory.getInstance( "X.509" );
        final KeyFactory keyFactory = KeyFactory.getInstance( "RSA" );

        final List<String> lines = readLines( is );
        String currentHeader = null;
        final StringBuilder current = new StringBuilder();
        final Map<String, String> entries = new LinkedHashMap<String, String>();
        for ( final String line : lines )
        {
            if ( line == null )
            {
                continue;
            }

            if ( line.startsWith( "-----BEGIN" ) )
            {
                currentHeader = line.trim();
                current.setLength( 0 );
            }
            else if ( line.startsWith( "-----END" ) )
            {
                entries.put( currentHeader, current.toString() );
            }
            else
            {
                current.append( line.trim() );
            }
        }

        final List<Certificate> certs = new ArrayList<Certificate>();
        for ( int pass = 0; pass < 2; pass++ )
        {
            for ( final Map.Entry<String, String> entry : entries.entrySet() )
            {
                final String header = entry.getKey();
                final byte[] data = decodeBase64( entry.getValue() );

                if ( pass > 0 && header.contains( "BEGIN PRIVATE KEY" ) )
                {
                    final KeySpec spec = new PKCS8EncodedKeySpec( data );
                    final PrivateKey key = keyFactory.generatePrivate( spec );
                    ks.setKeyEntry( "key", key, keyPass.toCharArray(), certs.toArray( new Certificate[] {} ) );
                }
                else if ( pass < 1 && header.contains( "BEGIN CERTIFICATE" ) )
                {
                    final Certificate c = certFactory.generateCertificate( new ByteArrayInputStream( data ) );

                    ks.setCertificateEntry( "certificate", c );
                    certs.add( c );
                }
            }
        }
    }

    public static void readCerts( final InputStream is, final String aliasPrefix, final KeyStore ks )
        throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException
    {
        final CertificateFactory certFactory = CertificateFactory.getInstance( "X.509" );

        final List<String> lines = readLines( is );
        final StringBuilder current = new StringBuilder();
        final List<String> entries = new ArrayList<String>();
        for ( final String line : lines )
        {
            if ( line == null )
            {
                continue;
            }

            if ( line.startsWith( "-----BEGIN" ) )
            {
                current.setLength( 0 );
            }
            else if ( line.startsWith( "-----END" ) )
            {
                entries.add( current.toString() );
            }
            else
            {
                current.append( line.trim() );
            }
        }

        int i = 0;
        for ( final String entry : entries )
        {
            final byte[] data = decodeBase64( entry );

            final Certificate c = certFactory.generateCertificate( new ByteArrayInputStream( data ) );

            ks.setCertificateEntry( aliasPrefix + i, c );
            i++;
        }
    }

    private static List<String> readLines( final InputStream is )
        throws IOException
    {
        final List<String> lines = new ArrayList<String>();
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader( new InputStreamReader( is ) );
            String line = null;
            while ( ( line = reader.readLine() ) != null )
            {
                lines.add( line.trim() );
            }
        }
        finally
        {
            closeQuietly( reader );
        }

        return lines;
    }
}
