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

package com.redhat.rcm.version.mgr.mod;

import static com.redhat.rcm.version.mgr.mod.Interpolations.ARTIFACT_ID;
import static com.redhat.rcm.version.mgr.mod.Interpolations.GROUP_ID;
import static com.redhat.rcm.version.mgr.mod.Interpolations.PROPERTIES;
import static com.redhat.rcm.version.mgr.mod.Interpolations.VERSION;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.Properties;

import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.model.Model;
import org.junit.Test;

import com.redhat.rcm.version.model.Project;

public class InterpolationsTest
{

    @Test
    public void avoidPropertyValueRecursion()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Properties p = new Properties();
        p.setProperty( "foo", "${foo}" );

        model.setProperties( p );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "${foo}" );
        src = PROPERTIES.interpolate( src, project );

        assertThat( src.toString(), equalTo( "${foo}" ) );
    }

    @Test
    public void resolveProperty_WholeValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Properties p = new Properties();
        p.setProperty( "foo", "bar" );

        model.setProperties( p );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "${foo}" );
        src = PROPERTIES.interpolate( src, project );

        assertThat( src.toString(), equalTo( "bar" ) );
    }

    @Test
    public void resolveProperty_FirstPartOfLargerValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Properties p = new Properties();
        p.setProperty( "foo", "bar" );

        model.setProperties( p );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "${foo}-bar" );
        src = PROPERTIES.interpolate( src, project );

        assertThat( src.toString(), equalTo( "bar-bar" ) );
    }

    @Test
    public void resolveProperty_LastPartOfLargerValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Properties p = new Properties();
        p.setProperty( "foo", "bar" );

        model.setProperties( p );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "baz-${foo}" );
        src = PROPERTIES.interpolate( src, project );

        assertThat( src.toString(), equalTo( "baz-bar" ) );
    }

    @Test
    public void resolveProperty_MiddlePartOfLargerValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Properties p = new Properties();
        p.setProperty( "foo", "bar" );

        model.setProperties( p );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "a-${foo}-c" );
        src = PROPERTIES.interpolate( src, project );

        assertThat( src.toString(), equalTo( "a-bar-c" ) );
    }

    @Test
    public void resolveGroupId_WholeValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "${project.groupId}" );
        src = GROUP_ID.interpolate( src, project );

        assertThat( src.toString(), equalTo( "org.test" ) );
    }

    @Test
    public void resolveGroupId_FirstPartOfLargerValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "${project.groupId}.test" );
        src = GROUP_ID.interpolate( src, project );

        assertThat( src.toString(), equalTo( "org.test.test" ) );
    }

    @Test
    public void resolveGroupId_LastPartOfLargerValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "test.${project.groupId}" );
        src = GROUP_ID.interpolate( src, project );

        assertThat( src.toString(), equalTo( "test.org.test" ) );
    }

    @Test
    public void resolveGroupId_MiddlePartOfLargerValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "test.${project.groupId}.test" );
        src = GROUP_ID.interpolate( src, project );

        assertThat( src.toString(), equalTo( "test.org.test.test" ) );
    }

    @Test
    public void resolveArtifactId_WholeValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "${project.artifactId}" );
        src = ARTIFACT_ID.interpolate( src, project );

        assertThat( src.toString(), equalTo( "test-project" ) );
    }

    @Test
    public void resolveArtifactId_FirstPartOfLargerValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "${project.artifactId}.test" );
        src = ARTIFACT_ID.interpolate( src, project );

        assertThat( src.toString(), equalTo( "test-project.test" ) );
    }

    @Test
    public void resolveArtifactId_LastPartOfLargerValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "test.${project.artifactId}" );
        src = ARTIFACT_ID.interpolate( src, project );

        assertThat( src.toString(), equalTo( "test.test-project" ) );
    }

    @Test
    public void resolveArtifactId_MiddlePartOfLargerValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "test.${project.artifactId}.test" );
        src = ARTIFACT_ID.interpolate( src, project );

        assertThat( src.toString(), equalTo( "test.test-project.test" ) );
    }

    @Test
    public void resolveVersion_WholeValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "${project.version}" );
        src = VERSION.interpolate( src, project );

        assertThat( src.toString(), equalTo( "1.0.2" ) );
    }

    @Test
    public void resolveVersion_FirstPartOfLargerValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "${project.version}.test" );
        src = VERSION.interpolate( src, project );

        assertThat( src.toString(), equalTo( "1.0.2.test" ) );
    }

    @Test
    public void resolveVersion_LastPartOfLargerValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "test.${project.version}" );
        src = VERSION.interpolate( src, project );

        assertThat( src.toString(), equalTo( "test.1.0.2" ) );
    }

    @Test
    public void resolveVersion_MiddlePartOfLargerValue()
        throws ProjectToolsException
    {
        Model model = new Model();
        model.setGroupId( "org.test" );
        model.setArtifactId( "test-project" );
        model.setVersion( "1.0.2" );

        Project project = new Project( model );

        StringBuilder src = new StringBuilder( "test.${project.version}.test" );
        src = VERSION.interpolate( src, project );

        assertThat( src.toString(), equalTo( "test.1.0.2.test" ) );
    }

}
