package com.redhat.rcm.version.model;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.apache.maven.model.Dependency;
import org.junit.Test;

public class DependencyManagementKeyTest
{

    @Test
    public void equalsUsingDefaults()
    {
        final String g = "org.foo";
        final String a = "bar";

        equalsTest( g, a, null, null, g, a, null, null, true );
    }

    @Test
    public void compareToUsingDefaults()
    {
        final String g = "org.foo";
        final String a = "bar";

        compareToTest( g, a, null, null, g, a, null, null, null );
    }

    @Test
    public void equalsUsingTestJarType()
    {
        final String g = "org.foo";
        final String a = "bar";
        final String t = "test-jar";

        equalsTest( g, a, t, null, g, a, t, null, true );
    }

    @Test
    public void compareToUsingTestJarType()
    {
        final String g = "org.foo";
        final String a = "bar";
        final String t = "test-jar";

        compareToTest( g, a, t, null, g, a, t, null, null );
    }

    @Test
    public void equalsUsingTestJarType_vsDefault_Fail()
    {
        final String g = "org.foo";
        final String a = "bar";
        final String t = "test-jar";

        equalsTest( g, a, t, null, g, a, null, null, false );
    }

    @Test
    public void compareToUsingTestJarType_vsDefault_Fail()
    {
        final String g = "org.foo";
        final String a = "bar";
        final String t = "test-jar";

        compareToTest( g, a, t, null, g, a, null, null, Boolean.TRUE );
    }

    private void equalsTest( final String g, final String a, final String t, final String c, final String g2,
                             final String a2, final String t2, final String c2, final boolean expectMatch )
    {
        final Dependency d1 = new Dependency();
        d1.setGroupId( g );
        d1.setArtifactId( a );
        d1.setVersion( "1" );
        d1.setType( t == null ? "jar" : t );
        d1.setClassifier( c );

        final DependencyManagementKey dmk1 = new DependencyManagementKey( d1 );

        final Dependency d2 = new Dependency();
        d2.setGroupId( g );
        d2.setArtifactId( a );
        d2.setVersion( "2" );
        d2.setType( t2 == null ? "jar" : t2 );
        d2.setClassifier( c2 );

        final DependencyManagementKey dmk2 = new DependencyManagementKey( d2 );

        if ( expectMatch )
        {
            assertThat( dmk1, equalTo( dmk2 ) );
        }
        else
        {
            assertThat( dmk1.equals( dmk2 ), equalTo( false ) );
        }
    }

    private void compareToTest( final String g, final String a, final String t, final String c, final String g2,
                                final String a2, final String t2, final String c2, final Boolean greaterThanZero )
    {
        final Dependency d1 = new Dependency();
        d1.setGroupId( g );
        d1.setArtifactId( a );
        d1.setVersion( "1" );
        d1.setType( t == null ? "jar" : t );
        d1.setClassifier( c );

        final DependencyManagementKey dmk1 = new DependencyManagementKey( d1 );

        final Dependency d2 = new Dependency();
        d2.setGroupId( g );
        d2.setArtifactId( a );
        d2.setVersion( "2" );
        d2.setType( t2 == null ? "jar" : t2 );
        d2.setClassifier( c2 );

        final DependencyManagementKey dmk2 = new DependencyManagementKey( d2 );

        if ( greaterThanZero == null )
        {
            assertThat( dmk1.compareTo( dmk2 ), equalTo( 0 ) );
        }
        else if ( greaterThanZero )
        {
            assertThat( dmk1.compareTo( dmk2 ) > 0, equalTo( true ) );
        }
        else
        {
            assertThat( dmk1.compareTo( dmk2 ) < 0, equalTo( true ) );
        }
    }

}
