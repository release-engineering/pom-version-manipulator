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
import static junit.framework.Assert.fail;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

public final class VManAssertions
{

    private VManAssertions()
    {
    }

    public static Set<Dependency> assertPOMsNormalizedToBOMs( final Collection<File> modified, final Set<File> boms,
                                                              final VersionlessProjectKey... interdepKeys )
        throws Exception
    {
        assertNotNull( modified );

        final Set<FullProjectKey> bomKeys = new HashSet<FullProjectKey>();
        for ( final File bom : boms )
        {
            bomKeys.add( loadProjectKey( bom ) );
        }

        final Set<Model> models = new HashSet<Model>( modified.size() );
        for ( final File file : modified )
        {
            models.add( loadModel( file ) );
        }

        return assertNormalized( models, bomKeys, interdepKeys );
    }

    public static Set<Dependency> assertProjectsNormalizedToBOMs( final Collection<Project> modified,
                                                                  final Set<Project> boms,
                                                                  final VersionlessProjectKey... interdepKeys )
        throws Exception
    {
        assertNotNull( modified );

        final Set<FullProjectKey> bomKeys = new HashSet<FullProjectKey>();
        for ( final Project bom : boms )
        {
            bomKeys.add( bom.getKey() );
        }

        final Set<Model> models = new HashSet<Model>( modified.size() );
        for ( final Project project : modified )
        {
            models.add( project.getModel() );
        }

        return assertNormalized( models, bomKeys, interdepKeys );
    }

    public static Set<Dependency> assertProjectNormalizedToBOMs( final Project project, final Set<Project> boms,
                                                                 final VersionlessProjectKey... interdepKeys )
        throws Exception
    {
        assertNotNull( project );

        final Set<FullProjectKey> bomKeys = new HashSet<FullProjectKey>();
        for ( final Project bom : boms )
        {
            bomKeys.add( bom.getKey() );
        }

        final Set<Model> models = Collections.singleton( project.getModel() );

        return assertNormalized( models, bomKeys, interdepKeys );
    }

    public static Set<Dependency> assertModelsNormalizedToBOMs( final Collection<Model> modified,
                                                                final Set<Project> boms,
                                                                final VersionlessProjectKey... interdepKeys )
        throws Exception
    {
        assertNotNull( modified );

        final Set<FullProjectKey> bomKeys = new HashSet<FullProjectKey>();
        for ( final Project bom : boms )
        {
            bomKeys.add( bom.getKey() );
        }

        return assertNormalized( new HashSet<Model>( modified ), bomKeys, interdepKeys );
    }

    public static Set<Dependency> assertModelsNormalizedToBOMs( final Model model, final Set<Project> boms,
                                                                final VersionlessProjectKey... interdepKeys )
        throws Exception
    {
        assertNotNull( model );

        final Set<FullProjectKey> bomKeys = new HashSet<FullProjectKey>();
        for ( final Project bom : boms )
        {
            bomKeys.add( bom.getKey() );
        }

        return assertNormalized( Collections.singleton( model ), bomKeys, interdepKeys );
    }

    private static Set<Dependency> assertNormalized( final Set<Model> modified, final Set<FullProjectKey> bomKeys,
                                                     final VersionlessProjectKey... skip )
        throws Exception
    {
        final Set<VersionlessProjectKey> skipKeys = new HashSet<VersionlessProjectKey>( Arrays.asList( skip ) );
        final Set<Dependency> skippedDeps = new HashSet<Dependency>();
        for ( final Model model : modified )
        {
            System.out.println( "Examining: " + model.getId() );

            new MavenXpp3Writer().write( System.out, model );

            final DependencyManagement dm = model.getDependencyManagement();
            if ( dm != null )
            {
                final Set<FullProjectKey> foundBoms = new HashSet<FullProjectKey>();

                for ( final Dependency dep : dm.getDependencies() )
                {
                    final VersionlessProjectKey key = new VersionlessProjectKey( dep.getGroupId(), dep.getArtifactId() );
                    if ( ( "pom".equals( dep.getType() ) && Artifact.SCOPE_IMPORT.equals( dep.getScope() ) ) )
                    {
                        foundBoms.add( new FullProjectKey( dep ) );
                    }
                    else if ( !skipKeys.contains( key ) )
                    {
                        assertNull( "Managed Dependency version was NOT nullified: " + dep + "\nPOM: " + model.getId(),
                                    dep.getVersion() );
                    }
                    else
                    {
                        skippedDeps.add( dep );
                    }
                }

                assertThat( foundBoms, equalTo( bomKeys ) );
            }

            for ( final Dependency dep : model.getDependencies() )
            {
                if ( !( "pom".equals( dep.getType() ) && Artifact.SCOPE_IMPORT.equals( dep.getScope() ) ) )
                {
                    final VersionlessProjectKey key = new VersionlessProjectKey( dep.getGroupId(), dep.getArtifactId() );
                    if ( !skipKeys.contains( key ) )
                    {
                        assertNull( "Dependency version was NOT nullified: " + dep.getManagementKey() + "\nPOM: "
                            + model.getId(), dep.getVersion() );
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

    public static void assertNoErrors( final VersionManagerSession session )
    {
        final List<Throwable> errors = session.getErrors();
        if ( errors != null && !errors.isEmpty() )
        {
            final StringBuilder sb = new StringBuilder();
            sb.append( errors.size() )
              .append( "errors encountered\n\n" );

            int idx = 1;
            for ( final Throwable error : errors )
            {
                final StringWriter sw = new StringWriter();
                error.printStackTrace( new PrintWriter( sw ) );

                sb.append( "\n" )
                  .append( idx )
                  .append( ".  " )
                  .append( sw.toString() );
                idx++;
            }

            sb.append( "\n\nSee above errors." );

            fail( sb.toString() );
        }
    }
}
