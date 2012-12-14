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
import java.util.Properties;
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
import org.commonjava.util.logging.Logger;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.util.DefaultRepositorySystemSession;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.maven.VManWorkspaceReader;
import com.redhat.rcm.version.maven.WildcardProjectKey;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.util.ActivityLog;

public class VersionManagerSession
    extends SimpleProjectToolsSession
{
    private final Logger logger = new Logger( getClass() );
    
    private final List<Throwable> errors = new ArrayList<Throwable>();

    private final Map<File, ActivityLog> logs = new LinkedHashMap<File, ActivityLog>();

    private final Map<VersionlessProjectKey, Set<VersionlessProjectKey>> childPluginRefs =
        new HashMap<VersionlessProjectKey, Set<VersionlessProjectKey>>();

    private final File backups;

    private final File downloads;

    private final boolean preserveFiles;

    private final File workspace;

    private final File reports;

    private final String versionSuffix;

    private String settingsXml;

    private File capturePom;

    private final ManagedInfo managedInfo;

    private final ChangeInfo changeInfo;

    private final boolean strict;

    private final String versionModifier;

    private VManWorkspaceReader workspaceReader;

    public VersionManagerSession( final File workspace, final File reports, final String versionSuffix,
                                  final String versionModifier, final Collection<String> removedPlugins,
                                  final Collection<String> removedTests, final Collection<String> extensionsWhitelist,
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

        managedInfo =
            new ManagedInfo( this, removedPlugins, removedTests, extensionsWhitelist, modderKeys, relocatedCoords,
                             propertyMappings );
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

    public Dependency getManagedDependency( final ProjectKey key )
    {
        return managedInfo.getManagedDependency( key );
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

    public MavenProject getToolchainProject()
    {
        return managedInfo.getToolchainProject();
    }

    public MavenProject getBOMProject( final FullProjectKey key )
    {
        return managedInfo.getBOMProject( key );
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

    public List<WildcardProjectKey> getRemovedTests()
    {
        return managedInfo.getRemovedTests();
    }

    public Set<VersionlessProjectKey> getExtensionsWhitelist()
    {
        return managedInfo.getExtensionsWhitelist();
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

    public void setRemoteRepositories( final String remoteRepositories )
        throws MalformedURLException
    {
        String id = "vman";
        int repoIndex = 1;
        final String repos[] = remoteRepositories.split( "," );
        final ArrayList<Repository> resolveRepos = new ArrayList<Repository>();

        for ( final String repository : repos )
        {
            String u = repository;
            final int idx = u.indexOf( '|' );
            if ( idx > 0 )
            {
                id = u.substring( 0, idx );
                u = u.substring( idx + 1 );
            }

            final Repository resolveRepo = new Repository();
            resolveRepo.setId( id + '-' + repoIndex++ );
            resolveRepo.setUrl( u );

            resolveRepos.add( resolveRepo );
        }
        setResolveRepositories( resolveRepos.toArray( new Repository[resolveRepos.size()] ) );
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

    public Project getCurrentProject( final ProjectKey key )
    {
        return managedInfo.getCurrentProject( key );
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

    public void setCurrentProjects( final Set<Project> projects )
    {
        managedInfo.setCurrentProjects( projects );
    }

    public void setWorkspaceReader( final VManWorkspaceReader workspaceReader )
    {
        this.workspaceReader = workspaceReader;
        final RepositorySystemSession rss = getRepositorySystemSession();

        final DefaultRepositorySystemSession drss;
        if ( rss == null )
        {
            drss = new DefaultRepositorySystemSession();
        }
        else if ( rss instanceof DefaultRepositorySystemSession )
        {
            drss = (DefaultRepositorySystemSession) rss;
        }
        else
        {
            drss = new DefaultRepositorySystemSession( rss );
        }

        drss.setWorkspaceReader( workspaceReader );
        initialize( drss, getProjectBuildingRequest(), getArtifactRepositoriesForResolution(),
                    getRemoteRepositoriesForResolution() );
    }

    public VManWorkspaceReader getWorkspaceReader()
    {
        return workspaceReader;
    }

    /*
     * This should search through the defined properties. It will look for
     * versionmapper.<groupId>-<artifactId>
     * version.<groupId>-<artifactId>
     * and return the value held there.
     */
    public String replacePropertyVersion( final Project project, final String groupId, final String artifactId )
    {
        String result = null;
        final Model model = project.getEffectiveModel();

        if ( model == null )
        {
            // TODO: This needs more thought.
            return null;
        }

        final String mapper = "versionmapper." + groupId + '-' + artifactId;
        final String direct = "version." + groupId + '-' + artifactId;

        Properties props = model.getProperties();
        Set<String> commonKeys = props.stringPropertyNames();

        for ( final String key : commonKeys )
        {
            result = evaluateKey (props, direct, mapper, key);
            if (result != null )
            {
                break;
            }
        }
        // Can't find a matching substitution in current pom chain; check the toolchain.
        if ( result == null )
        {
            props = getToolchainProject().getProperties();
            commonKeys = props.stringPropertyNames();
            for ( final String key : commonKeys )
            {
                result = evaluateKey (props, direct, mapper, key);
                if (result != null )
                {
                    break;
                }
            }
        }
        
        if ( result == null )
        {
            project.getModel()
                .getProperties()
                .setProperty( direct, "MISSING VERSION" );

            result = "${" + direct + "}";
        }
        else
        {
            logger.info( "Successfully located mapper property: " + result + " for " + groupId + ':' + artifactId);
        }

        return result;
    }

    private String evaluateKey (Properties props, String direct, String mapper, String key)
    {
        String result = null;

        if ( key.equals( mapper ) )
        {
            String value = props.getProperty( key );
            if (Character.isDigit (value.charAt (0)))
            {
                // Versionmapper references an explicit version e.g. 2.0.
                result = value;
            }
            else
            {
                result = "${" + value + "}";
            }
        }
        else if ( key.equals( direct ) )
        {
            result = "${" + direct + "}";
        }
        return result;
    }
}
