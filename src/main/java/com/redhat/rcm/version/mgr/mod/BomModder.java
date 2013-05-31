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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.ModelBase;
import org.apache.maven.model.Profile;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.DependencyManagementKey;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.model.ReadOnlyDependency;

@Component( role = ProjectModder.class, hint = "bom-realignment" )
public class BomModder
    implements ProjectModder
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Override
    public String getDescription()
    {
        return "Forcibly realign dependencies to use those declared in the supplied BOM file(s). Inject supplied BOM(s) into the project root POM.";
    }

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        final Model model = project.getModel();

        final List<ModelBase> bases = new ArrayList<ModelBase>();
        bases.add( model );

        final List<Profile> profiles = model.getProfiles();
        if ( profiles != null && !profiles.isEmpty() )
        {
            bases.addAll( profiles );
        }

        final File pom = project.getPom();
        boolean changed = false;

        for ( final ModelBase base : bases )
        {
            logger.info( "Processing: {} in model: {}", base, model );

            DependencyManagement dm = null;

            if ( base.getDependencies() != null )
            {
                logger.info( "Processing dependencies for '" + project.getKey() + "'..." );
                for ( final Iterator<Dependency> it = base.getDependencies()
                                                          .iterator(); it.hasNext(); )
                {
                    final Dependency dep = it.next();

                    logger.info( "Processing: {}", dep );

                    final DepModResult depResult = modifyDep( dep, project, pom, session, false );
                    if ( depResult == DepModResult.DELETED )
                    {
                        logger.info( "Removing: {}", dep );
                        it.remove();
                        changed = true;
                    }
                    else
                    {
                        if ( depResult == DepModResult.MODIFIED )
                        {
                            logger.info( "Modified {}", dep );
                        }
                        else
                        {
                            logger.info( "NO CHANGE to: {}", dep );
                        }

                        changed = DepModResult.MODIFIED == depResult || changed;
                    }
                }
            }

            if ( session.isStrict() )
            {
                dm = base.getDependencyManagement();

                if ( base.getDependencyManagement() != null && dm.getDependencies() != null )
                {
                    logger.info( "Processing dependencyManagement for '" + project.getKey() + "'..." );
                    for ( final Iterator<Dependency> it = dm.getDependencies()
                                                            .iterator(); it.hasNext(); )
                    {
                        final Dependency dep = it.next();

                        logger.info( "Processing: {}", dep );

                        final DepModResult depResult = modifyDep( dep, project, pom, session, true );
                        if ( depResult == DepModResult.DELETED )
                        {
                            logger.info( "Removing: {}", dep );
                            it.remove();
                            changed = true;
                        }
                        else
                        {
                            if ( depResult == DepModResult.MODIFIED )
                            {
                                logger.info( "Modified {}", dep );
                            }
                            else
                            {
                                logger.info( "NO CHANGE to: {}", dep );
                            }

                            changed = DepModResult.MODIFIED == depResult || changed;
                        }
                    }
                }
            }
        }

        // NOTE: Inject BOMs directly, but ONLY if the parent project is NOT in
        // the current projects list. (If the parent is a current project, we
        // want to inject the BOMs there instead.)
        final List<FullProjectKey> bomCoords = session.getBomCoords();
        logger.info( "%d BOMs available for injection...is my parent ({}) being modified in this session? {}",
                     bomCoords.size(), project.getParent(), session.isCurrentProject( project.getParent() ) );

        if ( !session.isCurrentProject( project.getParent() ) && bomCoords != null && !bomCoords.isEmpty() )
        {
            logger.info( "Injecting BOMs..." );
            DependencyManagement dm = model.getDependencyManagement();

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

                logger.info( "Adding BOM: {} at index: %d of {}", dep, insertCounter, model );

                changed = true;
                dm.getDependencies()
                  .add( insertCounter++, dep );
            }
        }
        else if ( !session.isStrict() && model.getDependencyManagement() != null )
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

    private DepModResult modifyDep( final Dependency d, final Project project, final File pom,
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

            key = new VersionlessProjectKey( d );
        }
        else
        {
            logger.info( "No relocation available for: " + key );
        }

        final String version = d.getVersion();

        if ( version == null )
        {
            session.getLog( pom )
                   .add( "NOT changing version for: %s%s. Version is inherited.", key, isManaged ? " [MANAGED]" : "" );
            return result;
        }

        Dependency managed = session.getManagedDependency( new DependencyManagementKey( d ) );
        if ( managed == null )
        {
            // if we don't find the one with the specific type/classifier, look for the generic one
            // if we find that, we can list the specific one as a missing dep
            managed = session.getManagedDependency( new DependencyManagementKey( d.getGroupId(), d.getArtifactId() ) );
            if ( managed != null )
            {
                session.addMissingDependency( project, d );
            }

            // in non-strict mode, we can make more assumptions.
            if ( session.isStrict() )
            {
                // now, reset it to null so we don't change the way the next section works in the absence of a matching BOM-managed dep.
                managed = null;
            }
        }

        // If in non-strict mode (default), wipe it out even if the dependency isn't in the BOM
        // ...assume it will be added from the capture POM.
        if ( managed != null || !session.isStrict() )
        {
            d.setVersion( null );

            final Dependency target = d.clone();
            if ( managed != null )
            {
                target.setVersion( managed.getVersion() );
            }

            if ( isManaged )
            {
                // TODO: Is this right?? Shouldn't we be looking at the relocated, interpolated one??
                //                if ( !overridesManagedInfo( dep, managed ) )
                if ( !overridesManagedInfo( d, managed ) )
                {
                    result = DepModResult.DELETED;
                }
                else
                {
                    d.setVersion( session.replacePropertyVersion( project, d.getGroupId(), d.getArtifactId(),
                                                                  d.getType(), d.getClassifier() ) );
                    result = DepModResult.MODIFIED;
                }
            }

            session.addDependencyModification( project.getVersionlessKey(), dep, target );
        }

        if ( managed == null )
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

    /**
     * Determines whether dependency dep overrides the mananged dependency (i.e.
     * is different).
     *
     * @param dep
     * @param managed
     * @return true if dep overrides.
     */
    private boolean overridesManagedInfo( final Dependency dep, final Dependency managed )
    {
        String depScope = dep.getScope();
        if ( depScope == null )
        {
            depScope = "compile";
        }

        String mgdScope = managed.getScope();
        if ( mgdScope == null )
        {
            mgdScope = "compile";
        }

        if ( !depScope.equals( mgdScope ) )
        {
            return true;
        }

        final Set<ComparableExclusion> depExclusions = new HashSet<ComparableExclusion>();
        final Set<ComparableExclusion> mgdExclusions = new HashSet<ComparableExclusion>();

        List<Exclusion> dEx = dep.getExclusions();
        if ( dEx == null )
        {
            dEx = Collections.emptyList();
        }
        for ( final Exclusion exclusion : dEx )
        {
            depExclusions.add( new ComparableExclusion( exclusion ) );
        }

        List<Exclusion> mEx = managed.getExclusions();
        if ( mEx == null )
        {
            mEx = Collections.emptyList();
        }

        for ( final Exclusion exclusion : mEx )
        {
            mgdExclusions.add( new ComparableExclusion( exclusion ) );
        }

        if ( depExclusions.isEmpty() )
        {
            return false;
        }

        // Compare to see if the exclusions are the same...if not, then the list differs and the local dep is overriding the managed one.
        if ( !mgdExclusions.equals( depExclusions ) )
        {
            return true;
        }

        return false;
    }

    private static enum DepModResult
    {
        UNCHANGED, MODIFIED, DELETED;
    }

    private static class ComparableExclusion
        extends Exclusion
    {
        private static final long serialVersionUID = 8470840709131335264L;

        public ComparableExclusion( final Exclusion e )
        {
            super();
            super.setGroupId( e.getGroupId() );
            super.setArtifactId( e.getArtifactId() );
        }

        @Override
        public boolean equals( final Object obj )
        {
            if ( this == obj )
            {
                return true;
            }
            if ( obj == null )
            {
                return false;
            }
            if ( !( obj instanceof Exclusion ) )
            {
                return false;
            }
            final ComparableExclusion other = (ComparableExclusion) obj;
            if ( getArtifactId() == null )
            {
                if ( other.getArtifactId() != null )
                {
                    return false;
                }
            }
            else if ( !getArtifactId().equals( other.getArtifactId() ) )
            {
                return false;
            }
            if ( getGroupId() == null )
            {
                if ( other.getGroupId() != null )
                {
                    return false;
                }
            }
            else if ( !getGroupId().equals( other.getGroupId() ) )
            {
                return false;
            }

            return true;

        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ( ( getArtifactId() == null ) ? 0 : getArtifactId().hashCode() );
            result = prime * result + ( ( getGroupId() == null ) ? 0 : getGroupId().hashCode() );
            return result;
        }

        @Override
        public String toString()
        {
            return "Exclusion : " + getGroupId() + ":" + getArtifactId();
        }
    }
}
