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

import org.apache.maven.model.Activation;
import org.apache.maven.model.ActivationProperty;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.codehaus.plexus.component.annotations.Component;
import org.commonjava.util.logging.Logger;

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

        if ( removedTests.contains( projectkey ) )
        {
            if ( model.getDependencies() != null )
            {
                final List<Dependency> movedDeps = new ArrayList<Dependency>();

                for ( final Iterator<Dependency> it = model.getDependencies()
                                                           .iterator(); it.hasNext(); )
                {
                    final Dependency dep = it.next();

                    // TODO: Try to retrieve the corresponding managed dependency, to see if it specifies a scope...
                    // This means checking the BOM(s) + the ancestry, since if we're operating in strict mode
                    // a parent POM could provide a managed dependency affecting the current one we're inspecting.
                    if ( dep.getScope() != null && dep.getScope()
                                                      .equals( "test" ) )
                    {
                        logger.info( "Removing scoped test dependency " + dep.toString() + " for '" + project.getKey()
                            + "'..." );
                        movedDeps.add( dep );
                        it.remove();
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
