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

import static com.redhat.rcm.version.testutil.TestProjectUtils.loadModel;
import static com.redhat.rcm.version.testutil.TestProjectUtils.newVersionManagerSession;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.redhat.rcm.version.model.Project;

public class TestRemovalTest
{

    protected File repo;

    protected File workspace;

    protected File reports;

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setupDirs()
        throws IOException
    {
        if ( repo == null )
        {
            repo = tempFolder.newFolder( "repository" );
        }

        if ( workspace == null )
        {
            workspace = tempFolder.newFolder( "workspace" );
        }

        if ( reports == null )
        {
            reports = tempFolder.newFolder( "reports" );
        }
    }

    @Test
    public void testRemoveTest()
        throws Exception
    {
        final Model model = loadModel( "bom-of-boms/repo/test/bom-child2/1/bom-child2-1.pom");

        assertThat( model.getProperties().size(), equalTo (0));
        
        final boolean changed = new TestRemovalModder().inject( new Project( model ),
            newVersionManagerSession
            ( workspace, reports, null, Collections.<String> emptyList(), 
              Collections.singletonList( "test:bom-child2" ) ) );

        assertThat( changed, equalTo( true ) );
        assertThat( model.getProperties().size(), equalTo(1));
        
        List<Dependency> dep = model.getDependencies();
        for (Dependency d : dep)
        {
            // Don't need to test groupId as there is only a single artifact with the GAV of 
            // junit in that pom.
            if (d.getArtifactId().equals( "junit" ))
            {
                fail ("Junit scoped dependency not removed");
            }
        }
        
    }
}
