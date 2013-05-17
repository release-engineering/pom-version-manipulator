package com.redhat.rcm.version.util;

import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.model.Dependency;

public class DepMapToString
{

    private final Map<Dependency, Dependency> deps;

    public DepMapToString( final Map<Dependency, Dependency> deps )
    {
        this.deps = deps;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();

        for ( final Entry<Dependency, Dependency> entry : deps.entrySet() )
        {
            final Dependency key = entry.getKey();
            final Dependency value = entry.getValue();

            sb.append( "  - " )
              .append( toString( key, sb ) )
              .append( " => " )
              .append( toString( value, sb ) );
        }

        return sb.toString();
    }

    public StringBuilder toString( final Dependency dep, final StringBuilder sb )
    {
        sb.append( dep.getGroupId() )
          .append( ':' )
          .append( dep.getArtifactId() )
          .append( ':' )
          .append( dep.getVersion() )
          .append( ':' )
          .append( dep.getType() )
          .append( dep.getClassifier() == null ? "" : ":" + dep.getClassifier() );

        return sb;
    }
}
