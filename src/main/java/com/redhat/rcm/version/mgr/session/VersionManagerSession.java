/*
 * Copyright (c) 2012 Red Hat, Inc.
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

package com.redhat.rcm.version.mgr.session;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.mae.project.session.SimpleProjectToolsSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Repository;
import org.apache.maven.project.MavenProject;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.model.ProjectAncestryGraph;
import com.redhat.rcm.version.util.ActivityLog;

public class VersionManagerSession
    extends SimpleProjectToolsSession
{

    public static final File GLOBAL = new File( "/" );

    private final List<Throwable> errors = new ArrayList<Throwable>();

    private final Map<File, ActivityLog> logs = new LinkedHashMap<File, ActivityLog>();

    private final Map<VersionlessProjectKey, Set<VersionlessProjectKey>> childPluginRefs =
        new HashMap<VersionlessProjectKey, Set<VersionlessProjectKey>>();

    private final File backups;

    private final File downloads;

    private final boolean preserveFiles;

    private final File workspace;

    private final File reports;

    private ProjectAncestryGraph ancestryGraph;

    private final String versionSuffix;

    private String settingsXml;

    private File capturePom;

    private final ManagedInfo managedInfo;

    private final ChangeInfo changeInfo;

    private final boolean strict;

    private final String versionModifier;

    public VersionManagerSession( final File workspace, final File reports, 
                                  final String versionSuffix, final String versionModifier,
                                  final Collection<String> removedPlugins, final Collection<String> removedTests, 
                                  final List<String> modderKeys, final boolean preserveFiles, final boolean strict,
                                  final Map<String, String> relocatedCoords, final Map<String, String> propertyMappings )
    {
        this.workspace = workspace;
        this.reports = reports;
        this.versionSuffix = versionSuffix;
        this.versionModifier = versionModifier;
        this.strict = strict;

        backups = new File( workspace, "backups" );
        backups.mkdirs();

        downloads = getDownloadsDir( workspace );

        this.preserveFiles = preserveFiles;

        managedInfo = new ManagedInfo( this, removedPlugins, removedTests, modderKeys, relocatedCoords, propertyMappings );
        changeInfo = new ChangeInfo();
    }

    public static File getDownloadsDir( final File workspace )
    {
        final File downloads = new File( workspace, "downloads" );
        downloads.mkdirs();

        return downloads;
    }

    public boolean isStrict()
    {
        return strict;
    }

    public String getVersionSuffix()
    {
        return versionSuffix;
    }

    public String getVersionModifier()
    {
        return versionModifier;
    }

    public FullProjectKey getRelocation( final ProjectKey key )
    {
        return managedInfo.getRelocation( key );
    }

    public Map<File, ActivityLog> getLogs()
    {
        return logs;
    }

    public synchronized ActivityLog getLog( final File pom )
    {
        ActivityLog log = logs.get( pom );
        if ( log == null )
        {
            log = new ActivityLog();
            logs.put( pom, log );
        }

        return log;
    }

    public void addUnmanagedPlugin( final File pom, final ReportPlugin plugin )
    {
        final Plugin p = new Plugin();
        p.setGroupId( plugin.getGroupId() );
        p.setArtifactId( plugin.getArtifactId() );
        p.setVersion( plugin.getVersion() );

        addUnmanagedPlugin( pom, p );
    }

    public synchronized VersionManagerSession addUnmanagedPlugin( final File pom, final Plugin plugin )
    {
        changeInfo.addUnmanagedPlugin( pom, plugin );

        return this;
    }

    public void addMissingParent( final Project project )
    {
        changeInfo.addMissingParent( project );
    }

    public synchronized VersionManagerSession addMissingDependency( final Project project, final Dependency dep )
    {
        changeInfo.addMissingDependency( project, dep );

        return this;
    }

    public VersionManagerSession addError( final Throwable error )
    {
        errors.add( error );
        return this;
    }

    public File getWorkspace()
    {
        return workspace;
    }

    public File getReports()
    {
        return reports;
    }

    public File getBackups()
    {
        return backups;
    }

    public File getDownloads()
    {
        return downloads;
    }

    public boolean isPreserveFiles()
    {
        return preserveFiles;
    }

    public Map<VersionlessProjectKey, Set<Plugin>> getUnmanagedPluginRefs()
    {
        return changeInfo.getUnmanagedPluginRefs();
    }

    public Map<File, Set<VersionlessProjectKey>> getUnmanagedPlugins()
    {
        return changeInfo.getUnmanagedPlugins();
    }

    public Set<VersionlessProjectKey> getUnmanagedPlugins( final File pom )
    {
        return changeInfo.getUnmanagedPlugins( pom );
    }

    public Set<Project> getProjectsWithMissingParent()
    {
        return changeInfo.getProjectsWithMissingParent();
    }

    public boolean isMissingParent( final Project project )
    {
        return changeInfo.isMissingParent( project );
    }

    public Map<VersionlessProjectKey, Set<Dependency>> getMissingDependencies()
    {
        return changeInfo.getMissingDependencies();
    }

    public Set<Dependency> getMissingDependencies( final VersionlessProjectKey key )
    {
        return changeInfo.getMissingDependencies( key );
    }

    public Map<VersionlessProjectKey, Set<File>> getMissingVersions()
    {
        return changeInfo.getMissingVersions();
    }

    public Set<VersionlessProjectKey> getMissingVersions( final ProjectKey key )
    {
        return changeInfo.getMissingVersions( key );
    }

    public List<Throwable> getErrors()
    {
        return errors;
    }

    public boolean hasDependencyMap()
    {
        return managedInfo.hasDependencyMap();
    }

    public String getArtifactVersion( final ProjectKey key )
    {
        return managedInfo.getArtifactVersion( key );
    }

    public Map<File, Map<VersionlessProjectKey, String>> getMappedDependenciesByBom()
    {
        return managedInfo.getMappedDependenciesByBom();
    }

    public List<FullProjectKey> getBomCoords()
    {
        return managedInfo.getBomCoords();
    }

    public VersionManagerSession addBOM( final File bom, final MavenProject project )
        throws VManException
    {
        managedInfo.addBOM( bom, project );

        return this;
    }

    public VersionManagerSession mapDependency( final File srcBom, final Dependency dep )
    {
        managedInfo.mapDependency( srcBom, dep );

        return this;
    }

    public CoordinateRelocations getRelocations()
    {
        return managedInfo.getRelocations();
    }

    public VersionManagerSession setToolchain( final File toolchainFile, final MavenProject project )
    {
        managedInfo.setToolchain( toolchainFile, project );

        return this;
    }

    public Set<VersionlessProjectKey> getRemovedPlugins()
    {
        return managedInfo.getRemovedPlugins();
    }

    public Set<VersionlessProjectKey> getRemovedTests()
    {
        return managedInfo.getRemovedTests();
    }

    public FullProjectKey getToolchainKey()
    {
        return managedInfo.getToolchainKey();
    }

    public Plugin getManagedPlugin( final VersionlessProjectKey key )
    {
        return managedInfo.getManagedPlugin( key );
    }

    public Map<VersionlessProjectKey, Plugin> getInjectedPlugins()
    {
        return managedInfo.getInjectedPlugins();
    }

    public Set<VersionlessProjectKey> getChildPluginReferences( final VersionlessProjectKey owner )
    {
        Set<VersionlessProjectKey> refs = childPluginRefs.get( owner );
        if ( refs == null )
        {
            refs = Collections.emptySet();
        }

        return refs;
    }

    public VersionManagerSession addChildPluginReference( final VersionlessProjectKey owner,
                                                          final VersionlessProjectKey plugin )
    {
        Set<VersionlessProjectKey> plugins = childPluginRefs.get( owner );
        if ( plugins == null )
        {
            plugins = new HashSet<VersionlessProjectKey>();
            childPluginRefs.put( owner, plugins );
        }

        plugins.add( plugin );

        return this;
    }

    public boolean isBom( final FullProjectKey key )
    {
        return managedInfo.hasBom( key );
    }

    // public boolean hasToolchainAncestor( final Project project )
    // {
    // return toolchainKey == null ? false : getAncestryGraph().hasAncestor( toolchainKey, project );
    // }
    //
    // public boolean hasParentInGraph( final Project project )
    // {
    // return getAncestryGraph().hasParentInGraph( project );
    // }

    // public VersionManagerSession addProject( final Project project )
    // {
    // getAncestryGraph().connect( project );
    // managedInfo.getCurrentProjects().add( project );
    //
    // return this;
    // }
    //
    private synchronized ProjectAncestryGraph getAncestryGraph()
    {
        if ( ancestryGraph == null )
        {
            ancestryGraph = new ProjectAncestryGraph( managedInfo.getToolchainKey() );
        }

        return ancestryGraph;
    }

    public boolean ancestryGraphContains( final FullProjectKey key )
    {
        return getAncestryGraph().contains( key );
    }

    public void setRemoteRepository( final String remoteRepository )
        throws MalformedURLException
    {
        String id = "vman";

        String u = remoteRepository;
        final int idx = u.indexOf( '|' );
        if ( idx > 0 )
        {
            id = u.substring( 0, idx );
            u = u.substring( idx + 1 );
        }

        // URL url = new URL( u );
        //
        // Authentication auth = null;
        //
        // String ui = url.getUserInfo();
        // if ( ui != null )
        // {
        // idx = ui.indexOf( ':' );
        //
        // String user = ui;
        // String password = null;
        //
        // if ( idx > 0 )
        // {
        // user = ui.substring( 0, idx );
        //
        // if ( idx + 1 < ui.length() )
        // {
        // password = ui.substring( idx + 1 );
        // }
        // }
        //
        // auth = new Authentication( user, password );
        // }
        //
        // RemoteRepository repo = new RemoteRepository( id, "default", u );
        // if ( auth != null )
        // {
        // repo.setAuthentication( auth );
        // }

        final Repository resolveRepo = new Repository();
        resolveRepo.setId( id );
        resolveRepo.setUrl( u );

        setResolveRepositories( resolveRepo );
        // setRemoteRepositoriesForResolution( Collections.singletonList( repo ) );
    }

    public boolean isToolchainReference( final Parent parent )
    {
        return managedInfo.isToolchainReference( parent );
    }

    public boolean inCurrentSession( final Parent parent )
    {
        return managedInfo.isCurrentProject( new VersionlessProjectKey( parent ) );
    }

    public void setSettingsXml( final String settingsXml )
    {
        this.settingsXml = settingsXml;
    }

    public String getSettingsXml()
    {
        return settingsXml;
    }

    public void setCapturePom( final File capturePom )
    {
        this.capturePom = capturePom;
    }

    public File getCapturePom()
    {
        return capturePom;
    }

    public void setCurrentProjects( final Collection<Model> models )
        throws ProjectToolsException
    {
        managedInfo.setCurrentProjects( models );
    }

    public Set<Project> getCurrentProjects()
    {
        return managedInfo.getCurrentProjects();
    }

    public boolean isCurrentProject( final ProjectKey key )
    {
        return managedInfo.isCurrentProject( key );
    }

    public boolean isCurrentProject( final Parent parent )
    {
        if ( parent == null )
        {
            return false;
        }

        return managedInfo.isCurrentProject( new FullProjectKey( parent ) );
    }

    public List<String> getModderKeys()
    {
        return managedInfo.getModderKeys();
    }

    public PropertyMappings getPropertyMappings()
    {
        return managedInfo.getPropertyMapping();
    }

    public Map<ProjectKey, FullProjectKey> getRelocatedCoordinates( final File pom )
    {
        return changeInfo.getRelocatedCoordinates( pom );
    }

    public void addRelocatedCoordinate( final File pom, final ProjectKey old, final FullProjectKey relocation )
    {
        changeInfo.addRelocatedCoordinate( pom, old, relocation );
    }

    public Map<File, Map<ProjectKey, FullProjectKey>> getRelocatedCoordinatesByFile()
    {
        return changeInfo.getRelocatedCoordinatesByFile();
    }
}
