package com.redhat.rcm.version.maven;

import java.util.regex.Pattern;

import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;

public class WildcardProjectKey extends VersionlessProjectKey
{
    Pattern groupPattern;
    Pattern artifactPattern;

    public WildcardProjectKey( final String groupId, final String artifactId )
    {
        super (groupId, artifactId);
        initPatterns();
    }

    public WildcardProjectKey( final Dependency dep )
    {
        super (dep);
        initPatterns();
    }

    public WildcardProjectKey( final Plugin plugin )
    {
        super (plugin);
        initPatterns();
    }

    public WildcardProjectKey( final Parent parent )
    {
        super (parent);
        initPatterns();
    }

    public WildcardProjectKey( final String ga )
    {
        super (ga);
        initPatterns();
    }

    public WildcardProjectKey( final ReportPlugin plugin )
    {
        super (plugin);
        initPatterns();
    }

    public WildcardProjectKey( final ProjectKey tk )
    {
        super (tk);
        initPatterns();
    }


    private void initPatterns()
    {
        groupPattern = Pattern.compile (this.getGroupId());
        artifactPattern = Pattern.compile (this.getArtifactId());
    }

    @Override
    public boolean equals( final Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( obj == null )
        {
            return false;
        }
        if ( getClass() != obj.getClass() )
        {
            return false;
        }
        final WildcardProjectKey other = (WildcardProjectKey) obj;
        if ( getArtifactId() == null )
        {
            if ( other.getArtifactId() != null )
            {
                return false;
            }
        }
        else if ( !artifactPattern.matcher( other.getArtifactId() ).matches() &&
                  !other.artifactPattern.matcher (getArtifactId() ).matches())
        {
            return false;
        }
        if ( getGroupId() == null )
        {
            if ( other.getGroupId () != null )
            {
                return false;
            }
        }
        else if ( !groupPattern.matcher( other.getGroupId() ).matches() &&
                  !other.groupPattern.matcher (getGroupId () ).matches() )
        {
            return false;
        }
        return true;
    }
}
