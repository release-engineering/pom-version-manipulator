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

package com.redhat.rcm.version.mgr.mod;

import static com.redhat.rcm.version.mgr.mod.Interpolations.interpolate;

import org.apache.log4j.Logger;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.model.ReadOnlyDependency;

import java.io.File;
import java.util.Iterator;

@Component( role = ProjectModder.class, hint = "BOM-realignment" )
public class BomModder
    implements ProjectModder
{

    private static final Logger LOGGER = Logger.getLogger( BomModder.class );

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        final Model model = project.getModel();
        final File pom = project.getPom();

        boolean changed = false;

        if ( model.getDependencies() != null )
        {
            LOGGER.info( "Processing dependencies for '" + project.getKey() + "'..." );
            for ( final Iterator<Dependency> it = model.getDependencies().iterator(); it.hasNext(); )
            {
                final Dependency dep = it.next();
                final DepModResult depResult = modifyDep( dep, model, project, pom, session, false );
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

        // NOTE: dependencyManagement BLOCKS the imported deps from the BOM. Nullify it!
        if ( model.getDependencyManagement() != null )
        {
            model.setDependencyManagement( null );
            changed = true;
        }

        // if ( model.getDependencyManagement() != null && model.getDependencyManagement().getDependencies() != null )
        // {
        // LOGGER.info( "Processing dependencyManagement for '" + project.getKey() + "'..." );
        // for ( final Iterator<Dependency> it = model.getDependencyManagement().getDependencies().iterator();
        // it.hasNext(); )
        // {
        // final Dependency dep = it.next();
        // final DepModResult depResult = modifyDep( dep, model, project, pom, session, true );
        // if ( depResult == DepModResult.DELETED )
        // {
        // it.remove();
        // changed = true;
        // }
        // else
        // {
        // changed = DepModResult.MODIFIED == depResult || changed;
        // }
        // }
        // }

        return changed;
    }

    private Dependency interpolateDep( final Dependency d, final Project project )
    {
        Dependency dep = d.clone();
        dep.setGroupId( interpolate( d.getGroupId(), project ) );
        dep.setArtifactId( interpolate( d.getArtifactId(), project ) );
        if ( dep.getVersion() != null )
        {
            dep.setVersion( interpolate( d.getVersion(), project ) );
        }

        if ( dep.getExclusions() != null && !dep.getExclusions().isEmpty() )
        {
            for ( final Exclusion ex : dep.getExclusions() )
            {
                ex.setGroupId( interpolate( ex.getGroupId(), project ) );
                ex.setArtifactId( interpolate( ex.getArtifactId(), project ) );
            }
        }

        // Interpolation is done, now LOCK IT DOWN!
        dep = new ReadOnlyDependency( dep );

        return dep;
    }

    private DepModResult modifyDep( final Dependency d, final Model model, final Project project, final File pom,
                                    final VersionManagerSession session, final boolean isManaged )
    {
        DepModResult result = DepModResult.UNCHANGED;

        final Dependency dep = interpolateDep( d, project );

        if ( project.getParent() == null && session.isBom( new FullProjectKey( dep ) ) )
        {
            return result;
        }

        VersionlessProjectKey key = new VersionlessProjectKey( dep.getGroupId(), dep.getArtifactId() );
        if ( session.isCurrentProject( key ) )
        {
            LOGGER.info( "NOT CHANGING version for interdependency from current project set: " + key );

            session.getLog( pom )
                   .add( "NOT changing version for: %s%s. This is an interdependency in the current project set.",
                         key,
                         isManaged ? " [MANAGED]" : "" );

            return result;
        }

        final FullProjectKey newKey = session.getRelocation( key );
        if ( newKey != null && !key.equals( newKey ) )
        {
            LOGGER.info( "Relocating dependency: " + key + " to: " + newKey );
            d.setGroupId( newKey.getGroupId() );
            d.setArtifactId( newKey.getArtifactId() );
            d.setVersion( newKey.getVersion() );
            result = DepModResult.MODIFIED;

            key = new VersionlessProjectKey( newKey );
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
            // wipe this out, and use the one in the BOM implicitly...DRY-style.
            d.setVersion( null );
            if ( isManaged && ( dep.getScope() == null || dep.getExclusions() == null || dep.getExclusions().isEmpty() ) )
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
            session.addMissingDependency( project, dep );
        }

        return result;
    }

    private static enum DepModResult
    {
        UNCHANGED, MODIFIED, DELETED;
    }

}
