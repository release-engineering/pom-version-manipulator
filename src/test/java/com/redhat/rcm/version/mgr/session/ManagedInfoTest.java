/*
 *  Copyright (C) 2012 John Casey.
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.redhat.rcm.version.mgr.session;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.Collections;

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.fixture.LoggingFixture;
import com.redhat.rcm.version.testutil.SessionBuilder;

public class ManagedInfoTest
{

    @Before
    public void setup()
    {
        LoggingFixture.setupLogging();
    }

    /**
     * Maven will NOT override locally specified managed dependencies. Maven imports managed dependencies
     * from BOMs such that they look like they have been declared locally. Therefore, when presented
     * with a list of BOM imports, Maven will NOT override managed dependencies from earlier BOMs
     * with those from later BOMs (since they look like they were declared locally).
     * 
     * VMan must mimic this behavior, and refuse to override managed dependencies from earlier BOMs
     * with those from later BOMs.
     */
    @Test
    public void dontOverridePreviousBOMDependency()
        throws VManException
    {
        final SessionBuilder sb = new SessionBuilder( null );
        final VersionManagerSession session = sb.build();
        final ManagedInfo info =
            new ManagedInfo( session, Collections.<String> emptyList(), Collections.<String> emptyList(), Collections.<String> emptyList(),
                             Collections.<String, String> emptyMap(), Collections.<String, String> emptyMap() );

        final Dependency dep1 = new Dependency();
        dep1.setGroupId( "org.foo" );
        dep1.setArtifactId( "bar" );
        dep1.setVersion( "1.0" );

        final DependencyManagement dm1 = new DependencyManagement();
        dm1.addDependency( dep1 );

        final Model model1 = new Model();
        model1.setGroupId( "g" );
        model1.setArtifactId( "bom-1" );
        model1.setVersion( "1" );
        model1.setPackaging( "pom" );
        model1.setDependencyManagement( dm1 );

        final MavenProject project1 = new MavenProject( model1 );

        info.addBOM( new File( "pom.1.xml" ), project1 );

        final Dependency dep2 = new Dependency();
        dep2.setGroupId( "org.foo" );
        dep2.setArtifactId( "bar" );
        dep2.setVersion( "1.1" );

        final DependencyManagement dm2 = new DependencyManagement();
        dm2.addDependency( dep2 );

        final Model model2 = new Model();
        model2.setGroupId( "g" );
        model2.setArtifactId( "bom-2" );
        model2.setVersion( "1" );
        model2.setPackaging( "pom" );
        model2.setDependencyManagement( dm2 );

        final MavenProject project2 = new MavenProject( model2 );

        info.addBOM( new File( "pom.2.xml" ), project2 );

        final VersionlessProjectKey pk = new VersionlessProjectKey( "org.foo", "bar" );
        assertThat( info.getArtifactVersion( pk ), equalTo( "1.0" ) );
    }

}
