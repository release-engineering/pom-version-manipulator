package com.redhat.rcm.version.util;

import org.apache.maven.model.Dependency;

public class DependencyToString
    implements ToStringProcessor<Dependency>
{

    @Override
    public String render( final Dependency value )
    {
        return value.getManagementKey();
    }

}
