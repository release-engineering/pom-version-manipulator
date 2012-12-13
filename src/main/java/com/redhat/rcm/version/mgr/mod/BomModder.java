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
                if ( modelBuilder != null && project.getEffectiveModel() == null )
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

        final String version = d.getVersion();

        if ( version == null )
        {
            session.getLog( pom )
                   .add( "NOT changing version for: %s%s. Version is inherited.", key, isManaged ? " [MANAGED]" : "" );
            return result;
        }

        final Dependency managed = session.getManagedDependency( key );

        // wipe this out, and use the one in the BOM implicitly...DRY-style.
        // If in non-strict mode (default), wipe it out even if the dependency isn't in the BOM
        // ...assume it will be added from the capture POM.
        if ( managed != null || !session.isStrict() )
        {
            d.setVersion( null );

            if ( isManaged )
            {
                logger.info("### Checking override for " + d.getGroupId() + " and " + d.getArtifactId());
                if ( !overridesManagedInfo( dep, managed ) )
                {
                    result = DepModResult.DELETED;
                }
                else
                {
                    d.setVersion( session.replacePropertyVersion( project, d.getGroupId(), d.getArtifactId() ) );
                    result = DepModResult.MODIFIED;
                }
            }
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
        @SuppressWarnings("unchecked")
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
            logger.info("### overridesManagedInfo :: dep scopes differ");
            return true;
        }

        final Set<ComparableExclusion>depExclusions  = new HashSet<ComparableExclusion>(  );

        Iterator<Exclusion> it = (Iterator<Exclusion>) (dep.getExclusions() == null ? Collections.emptyIterator() : dep.getExclusions().iterator());
        while (it.hasNext())
        {
            depExclusions.add(new ComparableExclusion(it.next()));
        }

        if ( depExclusions.isEmpty() )
        {
            logger.info("### overridesManagedInfo :: depExclusions is empty");
            return false;
        }

        final Set<ComparableExclusion>mgdExclusions  = new HashSet<ComparableExclusion>(  );

        it = (Iterator<Exclusion>) (managed.getExclusions() == null ? Collections.emptyIterator() : managed.getExclusions().iterator());
        while (it.hasNext())
        {
            mgdExclusions.add(new ComparableExclusion(it.next()));
        }

        // Compare to see if the exclusions are the same...if not, then the list differs and the local dep is overriding the managed one.#
        if ( ! mgdExclusions.equals( depExclusions ))
        {
            logger.info("### overridesManagedInfo :: mgd exclusions differ depExclusions " + depExclusions + " and " + mgdExclusions);
            return true;
        }

        return false;
    }

    private static enum DepModResult
    {
        UNCHANGED, MODIFIED, DELETED;
    }


    private static class ComparableExclusion extends Exclusion
    {
        private static final long serialVersionUID = 8470840709131335264L;

        public ComparableExclusion (Exclusion e)
        {
            super ();
            super.setGroupId(e.getGroupId());
            super.setArtifactId(e.getArtifactId());
        }

        @Override
        public boolean equals (Object obj)
        {
            if ( this == obj )
            {
                return true;
            }
            if ( obj == null )
            {
                return false;
            }
            if (! (obj instanceof Exclusion))
            {
                return false;
            }
            final ComparableExclusion other = (ComparableExclusion)obj;
            if ( getArtifactId() == null )
            {
                if ( other.getArtifactId() != null )
                {
                    return false;
                }
            }
            else if ( !getArtifactId().equals( other.getArtifactId() ))
            {
                return false;
            }
            if ( getGroupId() == null )
            {
                if ( other.getGroupId () != null )
                {
                    return false;
                }
            }
            else if ( !getGroupId ().equals( other.getGroupId() ))
            {
                return false;
            }

            return true;

        }

        @Override
        public int hashCode ()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ( ( getArtifactId () == null ) ? 0 : getArtifactId ().hashCode() );
            result = prime * result + ( ( getGroupId () == null ) ? 0 : getGroupId ().hashCode() );
            return result;
        }

        @Override
        public String toString ()
        {
            return "Exclusion : " + getGroupId () + ":" + getArtifactId ();
        }
    }
}
