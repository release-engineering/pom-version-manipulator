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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Activation;
import org.apache.maven.model.ActivationProperty;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.commonjava.util.logging.Logger;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.maven.EffectiveModelBuilder;
import com.redhat.rcm.version.maven.WildcardProjectKey;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectModder.class, hint = "testremoval" )
public class TestRemovalModder
    implements ProjectModder
{
    private final Logger logger = new Logger( getClass() );

    public static final String TEST_DEPS_PROFILE_ID = "_testDependencies";

    public static final String SKIP_TEST = "maven.test.skip";

    @Requirement
    private EffectiveModelBuilder modelBuilder;

    @Override
    public String getDescription()
    {
        return "Move test scoped dependencies from the pom into a separate profile.";
    }

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        final Model model = project.getModel();
        boolean changed = false;
        final List<WildcardProjectKey> removedTests = session.getRemovedTests();
        final WildcardProjectKey projectkey = new WildcardProjectKey( project.getGroupId(), project.getArtifactId() );

        if ( modelBuilder != null )
        {
            try
            {
                modelBuilder.loadEffectiveModel( project, session );
            }
            catch ( final VManException error )
            {
                logger.error( "Failed to build effective model for: %s. Reason: %s", error, project.getKey(),
                              error.getMessage() );
                session.addError( error );
            }
        }
        if ( removedTests.contains( projectkey ) )
        {
            if ( model.getDependencies() != null )
            {
                final List<Dependency> movedDeps = new ArrayList<Dependency>();

                for ( final Iterator<Dependency> it = model.getDependencies()
                                                           .iterator(); it.hasNext(); )
                {
                    final Dependency dep = it.next();
                    final VersionlessProjectKey depvpk =
                        new VersionlessProjectKey( dep.getGroupId(), dep.getArtifactId() );

                    if ( dep.getScope() != null && dep.getScope()
                                                      .equals( "test" ) )
                    {
                        logger.info( "Removing scoped test dependency " + dep.toString() + " for '" + project.getKey()
                            + "'..." );
                        movedDeps.add( dep );
                        it.remove();
                    }

                    // If we are inheriting the default scope from the managed dependency and that
                    // is test also move this to the inactive profile.
                    final Model effectivemodel = project.getEffectiveModel();
                    if ( effectivemodel != null )
                    {
                        final DependencyManagement depMgmt = effectivemodel.getDependencyManagement();
                        if ( depMgmt != null )
                        {
                            for ( final Dependency managedDep : depMgmt.getDependencies() )
                            {
                                final VersionlessProjectKey depmgmtvpk =
                                    new VersionlessProjectKey( managedDep.getGroupId(), managedDep.getArtifactId() );

                                if ( depvpk.equals( depmgmtvpk ) && dep.getScope() == null
                                    && managedDep.getScope() != null && managedDep.getScope()
                                                                                  .equals( "test" ) )
                                {
                                    logger.info( "Removing scoped test dependency " + managedDep.toString() + " for '"
                                        + project.getKey() + "'..." );
                                    movedDeps.add( managedDep );
                                    it.remove();
                                    break;
                                }
                            }
                        }
                    }

                }

                if ( !movedDeps.isEmpty() )
                {
                    final Profile profile = new Profile();
                    profile.setId( TEST_DEPS_PROFILE_ID );
                    profile.setDependencies( movedDeps );

                    final ActivationProperty actProp = new ActivationProperty();
                    actProp.setName( SKIP_TEST );
                    actProp.setValue( "false" );

                    final Activation act = new Activation();
                    act.setProperty( actProp );

                    profile.setActivation( act );

                    model.addProfile( profile );
                }
            }

            Properties props = model.getProperties();

            logger.info( "Injecting skip test property..." );
            if ( props == null )
            {
                props = new Properties();
            }

            props.put( SKIP_TEST, "true" );
            model.setProperties( props );

            changed = true;
        }
        return changed;
    }
}
