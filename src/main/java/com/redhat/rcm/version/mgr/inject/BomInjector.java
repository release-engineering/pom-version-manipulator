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

import java.io.File;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.mgr.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectInjector.class, hint = "BOM-realignment" )
public class BomInjector
    implements ProjectInjector
{

    private static final Logger LOGGER = Logger.getLogger( BomInjector.class );

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        Model model = project.getModel();
        File pom = project.getPom();

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

        if ( model.getDependencyManagement() != null && model.getDependencyManagement().getDependencies() != null )
        {
            LOGGER.info( "Processing dependencyManagement for '" + project.getKey() + "'..." );
            for ( final Iterator<Dependency> it = model.getDependencyManagement().getDependencies().iterator(); it.hasNext(); )
            {
                final Dependency dep = it.next();
                final DepModResult depResult = modifyDep( dep, model, project, pom, session, true );
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

    private DepModResult modifyDep( final Dependency dep, final Model model, final Project project, final File pom,
                                    final VersionManagerSession session, final boolean isManaged )
    {
        DepModResult result = DepModResult.UNCHANGED;

        if ( project.getParent() == null && session.isBom( new FullProjectKey( dep ) ) )
        {
            return result;
        }

        VersionlessProjectKey key = new VersionlessProjectKey( dep.getGroupId(), dep.getArtifactId() );
        final FullProjectKey newKey = session.getRelocation( key );
        if ( newKey != null && !key.equals( newKey ) )
        {
            LOGGER.info( "Relocating dependency: " + key + " to: " + newKey );
            dep.setGroupId( newKey.getGroupId() );
            dep.setArtifactId( newKey.getArtifactId() );
            dep.setVersion( newKey.getVersion() );

            key = new VersionlessProjectKey( newKey );
        }
        else
        {
            LOGGER.info( "No relocation available for: " + key );
        }

        String version = dep.getVersion();

        if ( version == null )
        {
            session.getLog( pom ).add( "NOT changing version for: %s%s. Version is inherited.", key,
                                       isManaged ? " [MANAGED]" : "" );
            return result;
        }

        version = session.getArtifactVersion( key );
        if ( version != null )
        {
            if ( !version.equals( dep.getVersion() ) )
            {
                session.getLog( pom ).add( "Changing version for: %s%s.\n\tFrom: %s\n\tTo: %s.", key,
                                           isManaged ? " [MANAGED]" : "", dep.getVersion(), version );

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
