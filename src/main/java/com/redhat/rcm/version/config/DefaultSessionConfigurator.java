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

import static com.redhat.rcm.version.util.InputUtils.getFile;
import static com.redhat.rcm.version.util.InputUtils.getFiles;

import java.io.File;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.mae.project.ProjectLoader;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.session.SessionInitializer;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.util.DefaultRepositorySystemSession;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.maven.VManWorkspaceReader;
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

    @Requirement
    private SessionInitializer sessionInitializer;

    DefaultSessionConfigurator()
    {
    }

    @Override
    public void configureSession( final List<String> boms, final String toolchain, final VersionManagerSession session )
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
    }

    private void loadSettings( final VersionManagerSession session )
    {
        MavenExecutionRequest executionRequest = session.getExecutionRequest();
        if ( executionRequest == null )
        {
            executionRequest = new DefaultMavenExecutionRequest();
        }

        File settingsXml;
        try
        {
            settingsXml = getFile( session.getSettingsXml(), session.getDownloads() );
        }
        catch ( final VManException e )
        {
            session.addError( e );
            return;
        }

        final DefaultSettingsBuildingRequest req = new DefaultSettingsBuildingRequest();
        req.setUserSettingsFile( settingsXml );
        req.setSystemProperties( System.getProperties() );

        try
        {
            final SettingsBuildingResult result = settingsBuilder.build( req );
            final Settings settings = result.getEffectiveSettings();

            executionRequest = requestPopulator.populateFromSettings( executionRequest, settings );
            session.setExecutionRequest( executionRequest );
        }
        catch ( final SettingsBuildingException e )
        {
            session.addError( new VManException( "Failed to build settings from: %s. Reason: %s", e, settingsXml,
                                                 e.getMessage() ) );
        }
        catch ( final MavenExecutionRequestPopulationException e )
        {
            session.addError( new VManException( "Failed to initialize system using settings from: %s. Reason: %s", e,
                                                 settingsXml, e.getMessage() ) );
        }

        try
        {
            sessionInitializer.initializeSessionComponents( session );
        }
        catch ( final ProjectToolsException e )
        {
            session.addError( e );
            return;
        }

        final RepositorySystemSession rss = session.getRepositorySystemSession();

        final DefaultRepositorySystemSession drss =
            (DefaultRepositorySystemSession) ( ( rss instanceof DefaultRepositorySystemSession ) ? rss
                            : new DefaultRepositorySystemSession( rss ) );

        drss.setWorkspaceReader( new VManWorkspaceReader( session ) );
    }

    private void loadToolchain( final String toolchain, final VersionManagerSession session )
    {
        File toolchainFile;
        try
        {
            toolchainFile = getFile( toolchain, session.getDownloads() );
        }
        catch ( final VManException e )
        {
            session.addError( e );
            return;
        }

        if ( toolchainFile != null )
        {
            MavenProject project;
            try
            {
                project = projectLoader.buildProjectInstance( toolchainFile, session );
            }
            catch ( final ProjectToolsException e )
            {
                session.addError( new VManException( "Error building toolchain: %s", e, e.getMessage() ) );
                return;
            }

            session.setToolchain( toolchainFile, project );
        }
    }

    private void loadBOMs( final List<String> boms, final VersionManagerSession session )
    {
        if ( !session.hasDependencyMap() )
        {
            File[] bomFiles = null;
            try
            {
                bomFiles = getFiles( boms, session.getDownloads() );
            }
            catch ( final VManException e )
            {
                session.addError( e );
            }

            List<MavenProject> projects = null;
            if ( bomFiles != null )
            {
                try
                {
                    projects = projectLoader.buildReactorProjectInstances( session, false, bomFiles );
                }
                catch ( final ProjectToolsException e )
                {
                    session.addError( new VManException( "Error building BOM: %s", e, e.getMessage() ) );
                }
            }

            if ( projects != null )
            {
                for ( final MavenProject project : projects )
                {
                    final File bom = project.getFile();

                    LOGGER.info( "Adding BOM to session: " + bom + "; " + project );
                    try
                    {
                        session.addBOM( bom, project );
                    }
                    catch ( final VManException e )
                    {
                        session.addError( e );
                    }
                }
            }
        }
    }

}
