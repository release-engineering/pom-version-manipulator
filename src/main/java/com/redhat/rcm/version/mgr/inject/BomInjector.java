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

package com.redhat.rcm.version.mgr.inject;

import org.apache.log4j.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.mgr.VersionManagerSession;
import com.redhat.rcm.version.model.FullProjectKey;
import com.redhat.rcm.version.model.ProjectKey;
import com.redhat.rcm.version.model.VersionlessProjectKey;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component( role = PomInjector.class, hint="BOM-realignment" )
public class BomInjector
    implements PomInjector
{
    
    private static final Logger LOGGER = Logger.getLogger( BomInjector.class );

    public boolean injectChanges( final MavenProject project, Map<FullProjectKey, MavenProject> projectMap, final VersionManagerSession session )
    {
        Model model = project.getOriginalModel();
        File pom = project.getFile();
        
        boolean changed = modifyCoord( model, pom, session );
        if ( session.isNormalizeBomUsage() )
        {
            LOGGER.info( "Introducing BOMs to '" + project.getId() + "'..." );
            changed = changed || introduceBoms( model, projectMap, pom, session );
        }

        if ( model.getDependencies() != null )
        {
            LOGGER.info( "Processing dependencies for '" + project.getId() + "'..." );
            for ( final Iterator<Dependency> it = model.getDependencies().iterator(); it.hasNext(); )
            {
                final Dependency dep = it.next();
                final DepModResult depResult = modifyDep( dep, pom, session, false );
                if ( depResult == DepModResult.DELETED )
                {
                    it.remove();
                    changed = true;
                }
                else
                {
                    changed = DepModResult.MODIFIED == depResult || changed;
                }
            }
        }

        if ( model.getDependencyManagement() != null && model.getDependencyManagement().getDependencies() != null )
        {
            LOGGER.info( "Processing dependencyManagement for '" + project.getId() + "'..." );
            for ( final Iterator<Dependency> it = model.getDependencyManagement().getDependencies().iterator(); it.hasNext(); )
            {
                final Dependency dep = it.next();
                final DepModResult depResult = modifyDep( dep, pom, session, true );
                if ( depResult == DepModResult.DELETED )
                {
                    it.remove();
                    changed = true;
                }
                else
                {
                    changed = DepModResult.MODIFIED == depResult || changed;
                }
            }
        }
        
        return changed;
    }
    
    private boolean modifyCoord( final Model model, final File pom, final VersionManagerSession session )
    {
        boolean changed = false;
        final Parent parent = model.getParent();

        String groupId = model.getGroupId();
        if ( groupId == null && parent != null )
        {
            groupId = parent.getGroupId();
        }

        if ( model.getVersion() != null )
        {
            final VersionlessProjectKey key = new VersionlessProjectKey( groupId, model.getArtifactId() );
//            final FullProjectKey newKey = session.getRelocation( key );
//
//            if ( newKey != null && !key.equals( newKey ) )
//            {
//                if ( groupId == model.getGroupId() )
//                {
//                    model.setGroupId( newKey.getGroupId() );
//                }
//                model.setArtifactId( newKey.getArtifactId() );
//            }

            final String version = session.getArtifactVersion( key );
            if ( version != null )
            {
                if ( !version.equals( model.getVersion() ) )
                {
                    session.getLog( pom ).add( "Changing POM version from: %s to: %s", model.getVersion(), version );
                    model.setVersion( version );
                    changed = true;
                }
                else
                {
                    session.getLog( pom ).add( "POM (%s) version is correct: %s", key, model.getVersion() );
                }
            }
            else
            {
                session.addMissingVersion( pom, key );
                session.getLog( pom ).add( "POM version is missing in BOM: %s", key );
            }
        }

        if ( parent != null )
        {
            final VersionlessProjectKey key = new VersionlessProjectKey( parent.getGroupId(), parent.getArtifactId() );
            final ProjectKey newKey = session.getRelocation( key );

            if ( newKey != null && !key.equals( newKey ) )
            {
                parent.setGroupId( newKey.getGroupId() );
                parent.setArtifactId( newKey.getArtifactId() );
            }

            final String version = session.getArtifactVersion( key );
            if ( version == null )
            {
                session.addMissingVersion( pom, key );
                session.getLog( pom ).add( "POM parent version is missing in BOM: %s", key );
            }
            else
            {
                if ( !version.equals( parent.getVersion() ) )
                {
                    session.getLog( pom ).add( "Changing POM parent (%s) version\n\tFrom: %s\n\tTo: %s",
                                               key,
                                               parent.getVersion(),
                                               version );
                    parent.setVersion( version );
                    changed = true;
                }
                else
                {
                    session.getLog( pom ).add( "POM parent (%s) version is correct: %s", key, parent.getVersion() );
                }
            }
        }

        return changed;
    }

    private boolean introduceBoms( final Model model, final Map<FullProjectKey, MavenProject> projects, final File pom,
                                   final VersionManagerSession session )
    {
        boolean changed = false;

        final Parent parent = model.getParent();
        if ( parent != null )
        {
            final FullProjectKey key = new FullProjectKey( parent );
            if ( projects.containsKey( key ) )
            {
                LOGGER.info( "Skipping BOM introduction for: '" + model.getId() + "'. Will modify parent POM (" + key
                    + ") instead..." );
                return changed;
            }
        }

        final Set<FullProjectKey> boms = new LinkedHashSet<FullProjectKey>( session.getBomCoords() );
        DependencyManagement dm = model.getDependencyManagement();
        if ( dm != null )
        {
            final List<Dependency> deps = dm.getDependencies();
            if ( deps != null )
            {
                for ( final Dependency dep : deps )
                {
                    LOGGER.info( "Checking managed dependency: " + dep );

                    if ( dep.getVersion() != null && Artifact.SCOPE_IMPORT.equals( dep.getScope() )
                        && "pom".equals( dep.getType() ) )
                    {
                        LOGGER.info( "Removing: " + dep + " from: " + pom );
                        final FullProjectKey k = new FullProjectKey( dep );
                        boms.remove( k );
                        changed = true;
                    }
                }
            }
        }
        else
        {
            LOGGER.info( "Introducing clean dependencyManagement section to contain BOMs..." );
            dm = new DependencyManagement();
            model.setDependencyManagement( dm );
            changed = true;
        }

        for ( final FullProjectKey bk : boms )
        {
            LOGGER.info( "Adding BOM: " + bk + " to: " + pom );
            dm.addDependency( bk.getBomDependency() );
            changed = true;
        }

        return changed;
    }

    private DepModResult modifyDep( final Dependency dep, final File pom, final VersionManagerSession session,
                                    final boolean isManaged )
    {
        DepModResult result = DepModResult.UNCHANGED;

        final VersionlessProjectKey key = new VersionlessProjectKey( dep.getGroupId(), dep.getArtifactId() );
        final FullProjectKey newKey = session.getRelocation( key );
        if ( newKey != null && !key.equals( newKey ) )
        {
            LOGGER.info( "Relocating dependency: " + key + " to: " + newKey );
            dep.setGroupId( newKey.getGroupId() );
            dep.setArtifactId( newKey.getArtifactId() );
            dep.setVersion( newKey.getVersion() );
        }
        else
        {
            LOGGER.info( "No relocation available for: " + key );
        }

        String version = dep.getVersion();

        if ( version == null )
        {
            session.getLog( pom ).add( "NOT changing version for: %s%s. Version is inherited.",
                                       key,
                                       isManaged ? " [MANAGED]" : "" );
            return result;
        }

        version = session.getArtifactVersion( key );
        if ( version != null )
        {
            if ( !version.equals( dep.getVersion() ) )
            {
                session.getLog( pom ).add( "Changing version for: %s%s.\n\tFrom: %s\n\tTo: %s.",
                                           key,
                                           isManaged ? " [MANAGED]" : "",
                                           dep.getVersion(),
                                           version );

                if ( session.isNormalizeBomUsage() )
                {
                    // wipe this out, and use the one in the BOM implicitly...DRY-style.
                    dep.setVersion( null );
                    if ( isManaged
                        && ( dep.getScope() == null || dep.getExclusions() == null || dep.getExclusions().isEmpty() ) )
                    {
                        result = DepModResult.DELETED;
                    }
                    else
                    {
                        result = DepModResult.MODIFIED;
                    }
                }
                else
                {
                    dep.setVersion( version );
                    result = DepModResult.MODIFIED;
                }
            }
            else if ( session.isNormalizeBomUsage() )
            {
                // wipe this out, and use the one in the BOM implicitly...DRY-style.
                dep.setVersion( null );
                if ( isManaged
                    && ( dep.getScope() == null || dep.getExclusions() == null || dep.getExclusions().isEmpty() ) )
                {
                    result = DepModResult.DELETED;
                }
                else
                {
                    result = DepModResult.MODIFIED;
                }
            }
            else
            {
                session.getLog( pom ).add( "Version for: %s%s is already correct.", key, isManaged ? " [MANAGED]" : "" );
            }
        }
        else
        {
            session.addMissingVersion( pom, key );
        }

        return result;
    }

    private static enum DepModResult
    {
        UNCHANGED, MODIFIED, DELETED;
    }

}
