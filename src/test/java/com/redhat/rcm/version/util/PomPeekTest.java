package com.redhat.rcm.version.util;

import static com.redhat.rcm.version.testutil.TestProjectFixture.getResourceFile;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.File;

import org.apache.log4j.Level;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.commonjava.util.logging.Log4jUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class PomPeekTest
{

    private static final String BASE = "pom-peek/";

    @BeforeClass
    public static void logging()
    {
        Log4jUtil.configure( Level.DEBUG );
    }

    @Test
    public void findGAVDirectlyInProjectAtTop()
    {
        final File pom = getResourceFile( BASE + "direct-gav-at-top.pom" );
        final PomPeek peek = new PomPeek( pom );

        assertThat( peek.getKey(), notNullValue() );

        final FullProjectKey key = peek.getKey();
        assertThat( key.getGroupId(), equalTo( "test" ) );
        assertThat( key.getArtifactId(), equalTo( "direct-gav-at-top" ) );
        assertThat( key.getVersion(), equalTo( "1" ) );

    }

    @Test
    public void findGAVDirectlyInProjectBelowProperties()
    {
        final File pom = getResourceFile( BASE + "direct-gav-below-props.pom" );
        final PomPeek peek = new PomPeek( pom );

        assertThat( peek.getKey(), notNullValue() );

        final FullProjectKey key = peek.getKey();
        assertThat( key.getGroupId(), equalTo( "test" ) );
        assertThat( key.getArtifactId(), equalTo( "direct-gav-below-props" ) );
        assertThat( key.getVersion(), equalTo( "1" ) );

    }

    @Test
    public void findGAVInheritedFromParentAtTop()
    {
        final File pom = getResourceFile( BASE + "inherited-gav-at-top.pom" );
        final PomPeek peek = new PomPeek( pom );

        assertThat( peek.getKey(), notNullValue() );

        final FullProjectKey key = peek.getKey();
        assertThat( key.getGroupId(), equalTo( "test" ) );
        assertThat( key.getArtifactId(), equalTo( "inherited-gav-at-top" ) );
        assertThat( key.getVersion(), equalTo( "1" ) );

    }

    @Test
    public void findGAVInheritedFromParentWithVersionOverrideAtTop()
    {
        final File pom = getResourceFile( BASE + "inherited-gav-with-override-at-top.pom" );
        final PomPeek peek = new PomPeek( pom );

        assertThat( peek.getKey(), notNullValue() );

        final FullProjectKey key = peek.getKey();
        assertThat( key.getGroupId(), equalTo( "test" ) );
        assertThat( key.getArtifactId(), equalTo( "inherited-gav-with-override-at-top" ) );
        assertThat( key.getVersion(), equalTo( "2" ) );

    }

}
