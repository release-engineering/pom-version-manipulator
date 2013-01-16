/*
 *  Copyright (C) 2011 John Casey.
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

package com.redhat.rcm.version.mgr.mod;

import static com.redhat.rcm.version.testutil.TestProjectFixture.getResourceFile;
import static com.redhat.rcm.version.testutil.TestProjectFixture.loadModel;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.apache.maven.mae.MAEException;
import org.apache.maven.model.Model;
import org.junit.Before;
import org.junit.Test;

import com.redhat.rcm.version.mgr.VersionManager;
import com.redhat.rcm.version.mgr.session.SessionBuilder;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.testutil.TestVersionManager;

public class PropertyModderTest
    extends AbstractModderTest
{

    private TestVersionManager vman;

    @Before
    public void setup()
        throws IOException, MAEException
    {
        if ( vman == null )
        {
            VersionManager.setClasspathScanning( true );
            vman = TestVersionManager.getInstance();
        }
    }

    @Test
    public void remapPropertyToLiteral()
        throws Exception
    {
        final Model model = loadModel( "pom-with-property.xml" );

        assertThat( model.getProperties()
                         .getProperty( "foo" ), equalTo( "bar" ) );

        final SessionBuilder builder = new SessionBuilder( workspace, reports ).withPropertyMapping( "foo", "baz" );

        final boolean changed = new PropertyModder().inject( new Project( model ), builder.build() );

        assertThat( changed, equalTo( true ) );
        assertThat( model.getProperties()
                         .getProperty( "foo" ), equalTo( "baz" ) );
    }

    @Test
    public void remapPropertyToResolvedSimpleExpression()
        throws Exception
    {
        final Model model = loadModel( "pom-with-property.xml" );
        final File bom = getResourceFile( "bom-with-property.xml" );

        assertThat( model.getProperties()
                         .getProperty( "foo" ), equalTo( "bar" ) );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withPropertyMapping( "foo", "@newFoo@" )
                                                    .build();

        vman.configureSession( Collections.singletonList( bom.getAbsolutePath() ), null, session );

        final boolean changed = new PropertyModder().inject( new Project( model ), session );

        assertThat( changed, equalTo( true ) );
        assertThat( model.getProperties()
                         .getProperty( "foo" ), equalTo( "baz" ) );
    }

    @Test
    public void dontRemapPropertyForUnresolvedSimpleExpression()
        throws Exception
    {
        final Model model = loadModel( "pom-with-property.xml" );
        final File bom = getResourceFile( "bom-with-property.xml" );

        assertThat( model.getProperties()
                         .getProperty( "foo" ), equalTo( "bar" ) );

        final VersionManagerSession session =
            new SessionBuilder( workspace, reports ).withPropertyMapping( "foo", "@otherFoo@" )
                                                    .build();

        vman.configureSession( Collections.singletonList( bom.getAbsolutePath() ), null, session );

        final boolean changed = new PropertyModder().inject( new Project( model ), session );

        assertThat( changed, equalTo( false ) );
        assertThat( model.getProperties()
                         .getProperty( "foo" ), equalTo( "bar" ) );
    }

}
