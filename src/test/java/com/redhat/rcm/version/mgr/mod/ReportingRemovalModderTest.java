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

import static com.redhat.rcm.version.testutil.TestProjectFixture.loadModel;
import static com.redhat.rcm.version.testutil.TestProjectFixture.newVersionManagerSession;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.apache.maven.model.Model;
import org.junit.Test;

import com.redhat.rcm.version.model.Project;

public class ReportingRemovalModderTest
    extends AbstractModderTest
{

    @Test
    public void removeReporting()
        throws Exception
    {
        final Model model = loadModel( "toolchain/child-reportPlugin-noVersion-1.0.pom" );

        assertThat( model.getReporting()
                         .getPlugins()
                         .size(), equalTo( 1 ) );

        final boolean changed =
            new ReportingRemovalModder().inject( new Project( model ),
                                                 newVersionManagerSession( workspace, reports, "-rebuild-1" ) );

        assertThat( changed, equalTo( true ) );
        assertThat( model.getReporting(), equalTo( null ) );
    }
}
