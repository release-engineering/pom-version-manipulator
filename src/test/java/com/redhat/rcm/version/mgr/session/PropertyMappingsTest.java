package com.redhat.rcm.version.mgr.session;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class PropertyMappingsTest
{

    @Test
    public void addMappingsAndRetrieveOneByKey()
    {
        final Map<String, String> mappings = new HashMap<String, String>();
        mappings.put( "foo", "bar" );
        mappings.put( "baz", "thbbbt" );

        final VersionManagerSession session = new SessionBuilder( null ).build();
        final PropertyMappings pm = new PropertyMappings( mappings, session );

        assertThat( pm.getMappedValue( "foo", session ), equalTo( "bar" ) );
    }

    @Test
    public void addMappingsAndRetrieveOneNullForMissingKey()
    {
        final Map<String, String> mappings = new HashMap<String, String>();
        mappings.put( "foo", "bar" );
        mappings.put( "baz", "thbbbt" );

        final VersionManagerSession session = new SessionBuilder( null ).build();
        final PropertyMappings pm = new PropertyMappings( mappings, session );

        assertThat( pm.getMappedValue( "blat", session ), nullValue() );
    }

    @Test
    public void addMappingWithExpressionAndRetrieveFullyResolved()
    {
        final Map<String, String> mappings = new HashMap<String, String>();
        mappings.put( "foo", "@baz@" );
        mappings.put( "baz", "thbbbt" );

        final VersionManagerSession session = new SessionBuilder( null ).build();
        final PropertyMappings pm = new PropertyMappings( mappings, session );

        assertThat( pm.getMappedValue( "foo", session ), equalTo( "thbbbt" ) );
    }

    @Test
    public void addMappingWithEmbeddedExpressionAndRetrieveFullyResolved()
    {
        final Map<String, String> mappings = new HashMap<String, String>();
        mappings.put( "foo", "blat, @baz@" );
        mappings.put( "baz", "thbbbt" );

        final VersionManagerSession session = new SessionBuilder( null ).build();
        final PropertyMappings pm = new PropertyMappings( mappings, session );

        assertThat( pm.getMappedValue( "foo", session ), equalTo( "blat, thbbbt" ) );
    }

    @Test
    public void addMappingWithTwoCopiesOfEmbeddedExpressionAndOneMissingExpression_RetrievePartiallyResolved()
    {
        final Map<String, String> mappings = new HashMap<String, String>();
        mappings.put( "foo", "blat, @baz@ >@missing@< @baz@" );
        mappings.put( "baz", "thbbbt" );

        final VersionManagerSession session = new SessionBuilder( null ).build();
        final PropertyMappings pm = new PropertyMappings( mappings, session );

        assertThat( pm.getMappedValue( "foo", session ), equalTo( "blat, thbbbt >@missing@< thbbbt" ) );
    }

    @Test
    public void addMappingWithExpressionCycle_RetrieveNull()
    {
        final Map<String, String> mappings = new HashMap<String, String>();
        mappings.put( "foo", "@baz@" );
        mappings.put( "baz", "@foo@" );

        final VersionManagerSession session = new SessionBuilder( null ).build();
        final PropertyMappings pm = new PropertyMappings( mappings, session );

        assertThat( pm.getMappedValue( "foo", session ), nullValue() );
    }

}
