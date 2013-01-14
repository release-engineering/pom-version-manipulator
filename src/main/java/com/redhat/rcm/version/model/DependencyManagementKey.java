package com.redhat.rcm.version.model;

import org.apache.maven.model.Dependency;

public class DependencyManagementKey
    implements Comparable<DependencyManagementKey>
{

    private final String groupId;

    private final String artifactId;

    private final String type;

    private final String classifier;

    public DependencyManagementKey( final String groupId, final String artifactId, final String type,
                                    final String classifier )
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.type = type;
        this.classifier = classifier;
    }

    public DependencyManagementKey( final String groupId, final String artifactId )
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.type = "jar";
        this.classifier = null;
    }

    public DependencyManagementKey( final Dependency dep )
    {
        this.groupId = dep.getGroupId();
        this.artifactId = dep.getArtifactId();
        this.type = dep.getType() == null ? "jar" : dep.getType();
        this.classifier = dep.getClassifier();
    }

    public String getGroupId()
    {
        return groupId;
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public String getType()
    {
        return type;
    }

    public String getClassifier()
    {
        return classifier;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( artifactId == null ) ? 0 : artifactId.hashCode() );
        result = prime * result + ( ( classifier == null ) ? 0 : classifier.hashCode() );
        result = prime * result + ( ( groupId == null ) ? 0 : groupId.hashCode() );
        result = prime * result + ( ( type == null ) ? 0 : type.hashCode() );
        return result;
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
        final DependencyManagementKey other = (DependencyManagementKey) obj;
        if ( artifactId == null )
        {
            if ( other.artifactId != null )
            {
                return false;
            }
        }
        else if ( !artifactId.equals( other.artifactId ) )
        {
            return false;
        }
        if ( classifier == null )
        {
            if ( other.classifier != null )
            {
                return false;
            }
        }
        else if ( !classifier.equals( other.classifier ) )
        {
            return false;
        }
        if ( groupId == null )
        {
            if ( other.groupId != null )
            {
                return false;
            }
        }
        else if ( !groupId.equals( other.groupId ) )
        {
            return false;
        }
        if ( type == null )
        {
            if ( other.type != null )
            {
                return false;
            }
        }
        else if ( !type.equals( other.type ) )
        {
            return false;
        }
        return true;
    }

    @Override
    public String toString()
    {
        return String.format( "%s:%s:%s%s", groupId, artifactId, type, ( classifier == null ? "" : ":" + classifier ) );
    }

    @Override
    public int compareTo( final DependencyManagementKey o )
    {
        return toString().compareTo( o.toString() );
    }

}
