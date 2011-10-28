package com.redhat.rcm.version.mgr.inject;

import static com.redhat.rcm.version.mgr.inject.Interpolations.ARTIFACT_ID;
import static com.redhat.rcm.version.mgr.inject.Interpolations.GROUP_ID;
import static com.redhat.rcm.version.mgr.inject.Interpolations.PROPERTIES;
import static com.redhat.rcm.version.mgr.inject.Interpolations.VERSION;
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
