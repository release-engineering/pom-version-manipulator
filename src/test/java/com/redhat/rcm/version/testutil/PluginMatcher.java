package com.redhat.rcm.version.testutil;

import java.util.HashSet;
import java.util.Set;

public class PluginMatcher
{
    public static final String MPLUGIN_GID = "org.apache.maven.plugins";

    public static final String UNSPECIFIED_VERSION = "$$unspecified";

    private final String g;

    private final String a;

    private String v = UNSPECIFIED_VERSION;

    private Set<String> eids;

    public static PluginMatcher mavenPlugin( final String a )
    {
        return new PluginMatcher( MPLUGIN_GID, a );
    }

    public static PluginMatcher plugin( final String g, final String a )
    {
        return new PluginMatcher( g, a );
    }

    private PluginMatcher( final String g, final String a )
    {
        this.g = g;
        this.a = a;
    }

    public PluginMatcher version( final String v )
    {
        this.v = v;
        return this;
    }

    public synchronized PluginMatcher execution( final String eid )
    {
        if ( eids == null )
        {
            eids = new HashSet<String>();
        }

        eids.add( eid );
        return this;
    }

    public String key()
    {
        return g + ":" + a;
    }

    public String v()
    {
        return v;
    }

    public String g()
    {
        return g;
    }

    public String a()
    {
        return a;
    }

    public Set<String> eids()
    {
        return eids;
    }
}
