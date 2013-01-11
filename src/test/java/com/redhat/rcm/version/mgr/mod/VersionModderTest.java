/*
 *  Copyright (c) 2012 Red Hat, Inc.
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

import static com.redhat.rcm.version.testutil.TestProjectFixture.loadModel;
import static com.redhat.rcm.version.testutil.TestProjectFixture.newVersionManagerSession;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.apache.maven.model.Model;
import org.junit.Test;

import com.redhat.rcm.version.model.Project;

public class VersionModderTest
    extends AbstractModderTest
{

    @Test
    public void testVersionReplaceVersion()
        throws Exception
    {
        final Model model = loadModel( "suffix/child-separateVersion-1.0.1.pom" );

        assertThat( model.getVersion(), equalTo( "1.0.1" ) );

        final boolean changed =
            new VersionModder().inject( new Project( model ),
                                        newVersionManagerSession( workspace, reports, "dummy-suffix", "1.0.1:Alpha1" ) );

        assertThat( changed, equalTo( true ) );
        assertThat( model.getVersion(), equalTo( "Alpha1" ) );
    }

    @Test
    public void testVersionReplaceSubset()
        throws Exception
    {
        final Model model = loadModel( "projects-with-property-refs/rwx-parent/0.2.1/rwx-parent-0.2.1.pom" );

        assertThat( model.getVersion(), equalTo( "0.2.1" ) );

        final boolean changed =
            new VersionModder().inject( new Project( model ),
                                        newVersionManagerSession( workspace, reports, "dummy-suffix", "1:Alpha1" ) );

        assertThat( changed, equalTo( true ) );
        assertThat( model.getVersion(), equalTo( "0.2.Alpha1" ) );
    }
}
