package com.redhat.rcm.version.mgr.session;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.redhat.rcm.version.testutil.SessionBuilder;

public class PropertyMappingsTest
{

    @Test
    public void addMappingsAndRetrieveOneByKey()
    {
        final Map<String, String> mappings = new HashMap<String, String>();
        mappings.put( "foo", "bar" );
        mappings.put( "baz", "thbbbt" );

        final PropertyMappings pm = new PropertyMappings( mappings, new SessionBuilder( null ).build() );

        assertThat( pm.getMappedValue( "foo" ), equalTo( "bar" ) );
    }

    @Test
    public void addMappingsAndRetrieveOneNullForMissingKey()
    {
        final Map<String, String> mappings = new HashMap<String, String>();
        mappings.put( "foo", "bar" );
        mappings.put( "baz", "thbbbt" );

        final PropertyMappings pm = new PropertyMappings( mappings, new SessionBuilder( null ).build() );

        assertThat( pm.getMappedValue( "blat" ), nullValue() );
    }

    @Test
    public void addMappingWithExpressionAndRetrieveFullyResolved()
    {
        final Map<String, String> mappings = new HashMap<String, String>();
        mappings.put( "foo", "@baz@" );
        mappings.put( "baz", "thbbbt" );

        final PropertyMappings pm = new PropertyMappings( mappings, new SessionBuilder( null ).build() );

        assertThat( pm.getMappedValue( "foo" ), equalTo( "thbbbt" ) );
    }

    @Test
    public void addMappingWithEmbeddedExpressionAndRetrieveFullyResolved()
    {
        final Map<String, String> mappings = new HashMap<String, String>();
        mappings.put( "foo", "blat, @baz@" );
        mappings.put( "baz", "thbbbt" );

        final PropertyMappings pm = new PropertyMappings( mappings, new SessionBuilder( null ).build() );

        assertThat( pm.getMappedValue( "foo" ), equalTo( "blat, thbbbt" ) );
    }

    @Test
    public void addMappingWithTwoCopiesOfEmbeddedExpressionAndOneMissingExpression_RetrievePartiallyResolved()
    {
        final Map<String, String> mappings = new HashMap<String, String>();
        mappings.put( "foo", "blat, @baz@ >@missing@< @baz@" );
        mappings.put( "baz", "thbbbt" );

        final PropertyMappings pm = new PropertyMappings( mappings, new SessionBuilder( null ).build() );

        assertThat( pm.getMappedValue( "foo" ), equalTo( "blat, thbbbt >@missing@< thbbbt" ) );
    }

}
