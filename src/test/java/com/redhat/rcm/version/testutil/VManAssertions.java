/*
 * Copyright (c) 2010 Red Hat, Inc.
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

package com.redhat.rcm.version.testutil;

import static com.redhat.rcm.version.testutil.TestProjectUtils.loadModel;
import static com.redhat.rcm.version.testutil.TestProjectUtils.loadProjectKey;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class VManAssertions
{

    private VManAssertions()
    {
    }

    public static Set<Dependency> assertNormalizedToBOMs( final Set<File> modified, final Set<File> boms,
                                                          final VersionlessProjectKey... interdepKeys )
        throws Exception
    {
        assertNotNull( modified );

        final Set<FullProjectKey> bomKeys = new HashSet<FullProjectKey>();
        for ( final File bom : boms )
        {
            bomKeys.add( loadProjectKey( bom ) );
        }

        final Set<VersionlessProjectKey> skipKeys = new HashSet<VersionlessProjectKey>( Arrays.asList( interdepKeys ) );
        final Set<Dependency> skippedDeps = new HashSet<Dependency>();
        for ( final File out : modified )
        {
            final Model model = loadModel( out );
            System.out.println( "Examining: " + out );

            new MavenXpp3Writer().write( System.out, model );

            // NOTE: Assuming injection of BOMs will happen in toolchain ancestor now...
            //
            // final DependencyManagement dm = model.getDependencyManagement();
            // if ( dm != null )
            // {
            // Set<FullProjectKey> foundBoms = new HashSet<FullProjectKey>();
            //
            // for ( final Dependency dep : dm.getDependencies() )
            // {
            // if ( ( "pom".equals( dep.getType() ) && Artifact.SCOPE_IMPORT.equals( dep.getScope() ) ) )
            // {
            // foundBoms.add( new FullProjectKey( dep ) );
            // }
            // else
            // {
            // assertNull( "Managed Dependency version was NOT nullified: " + dep.getManagementKey()
            // + "\nPOM: " + out, dep.getVersion() );
            // }
            // }
            //
            // assertThat( foundBoms, equalTo( bomKeys ) );
            // }

            for ( final Dependency dep : model.getDependencies() )
            {
                if ( !( "pom".equals( dep.getType() ) && Artifact.SCOPE_IMPORT.equals( dep.getScope() ) ) )
                {
                    final VersionlessProjectKey key = new VersionlessProjectKey( dep.getGroupId(), dep.getArtifactId() );
                    if ( !skipKeys.contains( key ) )
                    {
                        assertNull( "Dependency version was NOT nullified: " + dep.getManagementKey() + "\nPOM: " + out,
                                    dep.getVersion() );
                    }
                    else
                    {
                        skippedDeps.add( dep );
                    }
                }
            }
        }

        return skippedDeps;
    }
}
