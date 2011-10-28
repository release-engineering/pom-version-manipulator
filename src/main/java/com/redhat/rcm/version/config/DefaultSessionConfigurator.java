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

package com.redhat.rcm.version.config;

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
import java.util.Collection;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.log4j.Logger;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.mae.project.ProjectLoader;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

@Component( role = SessionConfigurator.class )
public class DefaultSessionConfigurator
    implements SessionConfigurator
{

    private static final Logger LOGGER = Logger.getLogger( DefaultSessionConfigurator.class );

    @Requirement
    private ProjectLoader projectLoader;

    @Requirement
    private SettingsBuilder settingsBuilder;

    @Requirement
    private MavenExecutionRequestPopulator requestPopulator;

    private final DefaultHttpClient client;

    DefaultSessionConfigurator()
    {
        client = new DefaultHttpClient();
        client.setRedirectStrategy( new DefaultRedirectStrategy() );
    }

    @Override
    public void configureSession( final List<String> boms, final String toolchain,
                                  final Collection<String> removedPlugins, final VersionManagerSession session )
        throws VManException
    {
        if ( session.getSettingsXml() != null )
        {
            loadSettings( session );
        }

        if ( boms != null )
        {
            loadBOMs( boms, session );
        }

        if ( toolchain == null )
        {
            // throw new VManException( "Toolchain POM must be specified!" );
        }
        else
        {
            loadToolchain( toolchain, session );
        }

        if ( removedPlugins != null )
        {
            session.setRemovedPlugins( removedPlugins );
        }
    }

    private void loadSettings( final VersionManagerSession session )
        throws VManException
    {
        MavenExecutionRequest executionRequest = session.getExecutionRequest();
        if ( executionRequest == null )
        {
            executionRequest = new DefaultMavenExecutionRequest();
        }

        File settingsXml = session.getSettingsXml();

        DefaultSettingsBuildingRequest req = new DefaultSettingsBuildingRequest();
        req.setUserSettingsFile( settingsXml );
        req.setSystemProperties( System.getProperties() );

        try
        {
            SettingsBuildingResult result = settingsBuilder.build( req );
            Settings settings = result.getEffectiveSettings();

            executionRequest = requestPopulator.populateFromSettings( executionRequest, settings );
            session.setExecutionRequest( executionRequest );
        }
        catch ( SettingsBuildingException e )
        {
            throw new VManException( "Failed to build settings from: %s. Reason: %s", e, settingsXml, e.getMessage() );
        }
        catch ( MavenExecutionRequestPopulationException e )
        {
            throw new VManException( "Failed to initialize system using settings from: %s. Reason: %s", e, settingsXml,
                                     e.getMessage() );
        }
    }

    private void loadToolchain( final String toolchain, final VersionManagerSession session )
        throws VManException
    {
        File toolchainFile = getFile( toolchain, session );
        if ( toolchainFile != null )
        {
            MavenProject project;
            try
            {
                project = projectLoader.buildProjectInstance( toolchainFile, session );
            }
            catch ( ProjectToolsException e )
            {
                session.addError( e );
                return;
            }

            session.setToolchain( toolchainFile, project );
        }
    }

    private void loadBOMs( final List<String> boms, final VersionManagerSession session )
        throws VManException
    {
        if ( !session.hasDependencyMap() )
        {
            final File[] bomFiles = getBomFiles( boms, session );

            List<MavenProject> projects;
            try
            {
                projects = projectLoader.buildReactorProjectInstances( session, false, bomFiles );
            }
            catch ( ProjectToolsException e )
            {
                session.addError( e );
                return;
            }

            if ( projects != null )
            {
                for ( MavenProject project : projects )
                {
                    File bom = project.getFile();

                    LOGGER.info( "Adding BOM to session: " + bom + "; " + project );
                    session.addBOM( bom, project );
                }
            }
        }
    }

    private File[] getBomFiles( final List<String> boms, final VersionManagerSession session )
        throws VManException
    {
        final List<File> result = new ArrayList<File>( boms.size() );

        for ( final String bom : boms )
        {
            File bomFile = getFile( bom, session );
            if ( bomFile != null )
            {
                result.add( bomFile );
            }
        }

        return result.toArray( new File[] {} );
    }

    private File getFile( final String location, final VersionManagerSession session )
        throws VManException
    {
        File result = null;

        if ( location.startsWith( "http" ) )
        {
            LOGGER.info( "Downloading BOM: '" + location + "'..." );

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

            final File downloaded = new File( session.getDownloads(), new File( location ).getName() );
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
                        session.addError( new VManException( "Received status: '%s' while downloading: %s",
                                                             response.getStatusLine(), location ) );
                    }
                }
                catch ( final ClientProtocolException e )
                {
                    session.addError( e );
                }
                catch ( final IOException e )
                {
                    session.addError( e );
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
