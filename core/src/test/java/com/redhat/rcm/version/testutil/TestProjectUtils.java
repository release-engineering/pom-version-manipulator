/*
 * Copyright (c) 2012 Red Hat, Inc.
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

import static com.redhat.rcm.version.util.InputUtils.getIncludedSubpaths;
import static junit.framework.Assert.fail;
import static org.apache.commons.io.FileUtils.copyFile;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;
import org.junit.rules.TemporaryFolder;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.mod.ProjectModder;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.util.InputUtils;

public final class TestProjectUtils
{

    private TestProjectUtils()
    {
    }

    public static VersionManagerSession newVersionManagerSession( final File workspace, final File reports,
                                                                  final String suffix, final String modifier )
    {
        return new SessionBuilder( workspace, reports ).withVersionSuffix( suffix )
                                                       .withVersionModifier( modifier )
                                                       .build();
    }

    public static VersionManagerSession newVersionManagerSession( final File workspace, final File reports,
                                                                  final String suffix )
    {
        return new SessionBuilder( workspace, reports ).withVersionSuffix( suffix )
                                                       .build();
    }

    public static VersionManagerSession newVersionManagerSession( final File workspace, final File reports,
                                                                  final String suffix,
                                                                  final Collection<String> removedPlugins,
                                                                  final Collection<String> removedTests )
    {
        return new SessionBuilder( workspace, reports ).withVersionSuffix( suffix )
                                                       .withRemovedPlugins( removedPlugins )
                                                       .withRemovedTests( removedTests )
                                                       .build();
    }

    public static Set<String> getStandardModders()
    {
        return new HashSet<String>( Arrays.asList( ProjectModder.STANDARD_MODIFICATIONS ) );
    }

    public static File getResourceFile( final String path )
    {
        final URL resource = Thread.currentThread()
                                   .getContextClassLoader()
                                   .getResource( path );
        if ( resource == null )
        {
            fail( "Resource not found: " + path );
        }

        return new File( resource.getPath() );
    }

    public static File getResourceFileCopy( final String path, final TemporaryFolder tempFolder )
        throws IOException
    {
        final File src = getResourceFile( path );
        final File dest = tempFolder.newFile( new File( path ).getName() );
        copyFile( src, dest );

        return dest;
    }

    public static Model loadModel( final String path )
        throws IOException
    {
        final File pom = getResourceFile( path );
        return loadModel( pom );
    }

    public static Model loadModel( final File pom )
        throws IOException
    {
        final Map<String, Object> options = new HashMap<String, Object>();
        options.put( ModelReader.IS_STRICT, Boolean.FALSE.toString() );

        return new DefaultModelReader().read( pom, options );
    }

    public static MavenProject mavenProjectFor( final Project project )
    {
        final MavenProject result = new MavenProject( project.getModel() );
        result.setOriginalModel( project.getModel() );
        result.setFile( project.getPom() );

        return result;
    }

    public static Set<Project> loadProjects( final String path, final VersionManagerSession session )
        throws Exception
    {
        final File dir = getResourceFile( path );
        final String[] poms = getIncludedSubpaths( dir, null, null, session );

        final Set<Project> projects = new HashSet<Project>( poms.length );
        for ( final String fname : poms )
        {
            final File f = new File( dir, fname );
            projects.add( loadProject( f ) );
        }

        return projects;
    }

    public static Set<Project> loadProjects( final File dir, final VersionManagerSession session )
        throws Exception
    {
        final String[] poms = InputUtils.getIncludedSubpaths( dir, null, null, session );

        final Set<Project> projects = new HashSet<Project>( poms.length );
        for ( final String fname : poms )
        {
            final File f = new File( dir, fname );
            projects.add( loadProject( f ) );
        }

        return projects;
    }

    public static Project loadProject( final String path )
        throws Exception
    {
        final File pom = getResourceFile( path );
        return new Project( pom, loadModel( pom ) );
    }

    public static Project loadProject( final File pom )
        throws Exception
    {
        final Map<String, Object> options = new HashMap<String, Object>();
        options.put( ModelReader.IS_STRICT, Boolean.FALSE.toString() );

        return new Project( pom, loadModel( pom ) );
    }

    public static FullProjectKey loadProjectKey( final String path )
        throws ProjectToolsException, IOException
    {
        final Model model = loadModel( path );

        return new FullProjectKey( model );
    }

    public static FullProjectKey loadProjectKey( final File pom )
        throws ProjectToolsException, IOException
    {
        final Model model = loadModel( pom );

        return new FullProjectKey( model );
    }

    public static Set<Model> loadModels( final Set<File> poms )
        throws VManException, IOException
    {
        final Set<Model> models = new LinkedHashSet<Model>( poms.size() );
        for ( final File pom : poms )
        {
            models.add( loadModel( pom ) );
        }

        return models;
    }

    public static void dumpModel( final Model model )
        throws IOException
    {
        final StringWriter writer = new StringWriter();
        new MavenXpp3Writer().write( writer, model );

        System.out.println( "\n\n" + writer.toString() + "\n\n" );
    }
}
