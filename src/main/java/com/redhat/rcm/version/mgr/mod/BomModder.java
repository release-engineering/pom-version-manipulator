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

import java.io.File;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.commonjava.util.logging.Logger;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.maven.EffectiveModelBuilder;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.model.ReadOnlyDependency;

@Component( role = ProjectModder.class, hint = "bom-realignment" )
public class BomModder
    implements ProjectModder
{
    private final Logger logger = new Logger( getClass() );

    @Requirement
    private EffectiveModelBuilder modelBuilder;

    @Override
    public String getDescription()
    {
        return "Forcibly realign dependencies to use those declared in the supplied BOM file(s). Inject supplied BOM(s) into the project root POM.";
    }

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        final Model model = project.getModel();
        final File pom = project.getPom();

        DependencyManagement dm = null;
        boolean changed = false;

        if ( model.getDependencies() != null )
        {
            logger.info( "Processing dependencies for '" + project.getKey() + "'..." );
            for ( final Iterator<Dependency> it = model.getDependencies()
                                                       .iterator(); it.hasNext(); )
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

        if ( session.isStrict() )
        {
            dm = model.getDependencyManagement();

            if ( model.getDependencyManagement() != null && dm.getDependencies() != null )
            {
                logger.info( "Processing dependencyManagement for '" + project.getKey() + "'..." );
                for ( final Iterator<Dependency> it = dm.getDependencies()
                                                        .iterator(); it.hasNext(); )
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
        }

        // NOTE: Inject BOMs directly, but ONLY if the parent project is NOT in
        // the current projects list. (If the parent is a current project, we
        // want to inject the BOMs there instead.)
        final List<FullProjectKey> bomCoords = session.getBomCoords();
        if ( !session.isCurrentProject( project.getParent() ) && bomCoords != null && !bomCoords.isEmpty() )
        {
            if ( dm == null )
            {
                dm = new DependencyManagement();
                model.setDependencyManagement( dm );
            }

            // Used to track inserting the BOMs in the correct order in the dependencyMgmt
            // section.
            int insertCounter = 0;

            for ( final FullProjectKey bomCoord : bomCoords )
            {
                final Dependency dep = new Dependency();
                dep.setGroupId( bomCoord.getGroupId() );
                dep.setArtifactId( bomCoord.getArtifactId() );
                dep.setVersion( bomCoord.getVersion() );
                dep.setType( "pom" );
                dep.setScope( Artifact.SCOPE_IMPORT );

                changed = true;
                dm.getDependencies()
                  .add( insertCounter++, dep );

                logger.info( "Injecting BOM " + dep.toString() + " into " + model );
            }
        }
        else if ( model.getDependencyManagement() != null )
        {
            model.setDependencyManagement( null );
            changed = true;
        }

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

        if ( dep.getExclusions() != null && !dep.getExclusions()
                                                .isEmpty() )
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
            logger.info( "NOT CHANGING version for interdependency from current project set: " + key );

            session.getLog( pom )
                   .add( "NOT changing version for: %s%s. This is an interdependency in the current project set.", key,
                         isManaged ? " [MANAGED]" : "" );

            return result;
        }

        final FullProjectKey newKey = session.getRelocation( key );
        if ( newKey != null && !key.equals( newKey ) )
        {
            logger.info( "Relocating dependency: " + key + " to: " + newKey );
            session.addRelocatedCoordinate( pom, key, newKey );

            d.setGroupId( newKey.getGroupId() );
            d.setArtifactId( newKey.getArtifactId() );
            d.setVersion( newKey.getVersion() );
            result = DepModResult.MODIFIED;

            key = new VersionlessProjectKey( newKey );
        }
        else
        {
            logger.info( "No relocation available for: " + key );
        }

        String version = d.getVersion();

        if ( version == null )
        {
            session.getLog( pom )
                   .add( "NOT changing version for: %s%s. Version is inherited.", key, isManaged ? " [MANAGED]" : "" );
            return result;
        }

        version = session.getArtifactVersion( key );

        // wipe this out, and use the one in the BOM implicitly...DRY-style.
        // If in non-strict mode (default), wipe it out even if the dependency isn't in the BOM
        // ...assume it will be added from the capture POM.
        if ( version != null || !session.isStrict() )
        {
            d.setVersion( null );
            if ( isManaged && ( dep.getScope() == null && ( dep.getExclusions() == null || dep.getExclusions()
                                                                                              .isEmpty() ) ) )
            {
                if ( dep.getScope() == null && ( dep.getExclusions() == null || dep.getExclusions()
                                                                                   .isEmpty() ) )
                {
                    result = DepModResult.DELETED;
                }
                else
                {
                    // FIXME: This is a BAD place to be...we can't remove the managed dep, but we also can't leave it.
                    // Leaving it means dependency version info will cause cascading build problems when versions shift.
                }
            }
            else
            {
                // This is expensive, so only do it on demand.
                // NOTE: After this, the project's effective model will be set (by the effective model builder)
                if ( project.getEffectiveModel() == null )
                {
                    if ( modelBuilder != null )
                    {
                        try
                        {
                            modelBuilder.getEffectiveModel( project, session );
                        }
                        catch ( final VManException e )
                        {
                            logger.error( "Failed to build effective model for: %s. Reason: %s", e, project.getKey(),
                                          e.getMessage() );
                            session.addError( e );
                        }
                    }
                }

                d.setVersion( session.replacePropertyVersion( project, d.getGroupId(), d.getArtifactId() ) );
                result = DepModResult.MODIFIED;
            }
        }

        if ( version == null )
        {
            // if we're in strict mode and this is a BOM, don't add it to the missing list.
            if ( !session.isStrict() || !"pom".equals( dep.getType() ) || !"import".equals( dep.getScope() ) )
            {
                // log this dependency as missing from the BOM(s) to can be captured and added.
                session.addMissingDependency( project, dep );
            }
        }

        return result;
    }

    private static enum DepModResult
    {
        UNCHANGED, MODIFIED, DELETED;
    }

}
