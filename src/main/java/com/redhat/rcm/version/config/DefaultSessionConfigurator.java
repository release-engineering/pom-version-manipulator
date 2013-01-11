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
import static org.apache.commons.lang.StringUtils.join;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.mae.project.ProjectLoader;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.session.SessionInitializer;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.commonjava.util.logging.Logger;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.maven.VManWorkspaceReader;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.util.PomPeek;

@Component( role = SessionConfigurator.class )
public class DefaultSessionConfigurator
    implements SessionConfigurator
{

    private final Logger logger = new Logger( getClass() );

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
    public void configureSession( final List<String> boms, final String toolchain, final VersionManagerSession session,
                                  final File[] pomFiles )
    {
        if ( session.getSettingsXml() != null )
        {
            loadSettings( session );
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

        if ( session.getWorkspaceReader() == null )
        {
            final VManWorkspaceReader workspaceReader = new VManWorkspaceReader( session );
            session.setWorkspaceReader( workspaceReader );
        }

        final Set<File> poms = new HashSet<File>();
        poms.addAll( Arrays.asList( pomFiles ) );

        if ( boms != null )
        {
            final File[] bomFiles = loadBOMs( boms, session );
            if ( bomFiles != null && bomFiles.length > 0 )
            {
                poms.addAll( Arrays.asList( bomFiles ) );
            }
        }

        if ( toolchain != null )
        {
            final File toolchainFile = loadToolchain( toolchain, session );
            if ( toolchainFile != null )
            {
                poms.add( toolchainFile );
            }
        }

        if ( session.getPeekedPoms()
                    .isEmpty() )
        {
            final Map<FullProjectKey, File> peekPoms = peekPoms( poms );
            session.setPeekedPoms( peekPoms );
        }
    }

    private Map<FullProjectKey, File> peekPoms( final Set<File> poms )
    {
        final Map<FullProjectKey, File> result = new HashMap<FullProjectKey, File>();
        for ( final File pom : poms )
        {
            final PomPeek peek = new PomPeek( pom );
            final FullProjectKey key = peek.getKey();
            if ( key != null )
            {
                result.put( key, pom );
            }
        }

        final List<FullProjectKey> keys = new ArrayList<FullProjectKey>( result.keySet() );
        Collections.sort( keys );

        logger.info( "PEEKed the following coordinates from pom file-list:\n\n  %s\n\n%d POMs could not be PEEKed.",
                     join( keys, "\n  " ), ( poms.size() - keys.size() ) );

        return result;
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
    }

    private File loadToolchain( final String toolchain, final VersionManagerSession session )
    {
        File toolchainFile = null;
        try
        {
            toolchainFile = getFile( toolchain, session.getDownloads() );
        }
        catch ( final VManException e )
        {
            session.addError( e );
        }

        if ( toolchainFile != null )
        {
            MavenProject project = null;
            try
            {
                project = projectLoader.buildProjectInstance( toolchainFile, session );
            }
            catch ( final ProjectToolsException e )
            {
                session.addError( new VManException( "Error building toolchain: %s", e, e.getMessage() ) );
            }

            if ( project != null )
            {
                session.setToolchain( toolchainFile, project );
            }
        }

        return toolchainFile;
    }

    private File[] loadBOMs( final List<String> boms, final VersionManagerSession session )
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

                    logger.info( "Adding BOM to session: " + bom + "; " + project );
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

            return bomFiles;
        }

        return null;
    }

}
