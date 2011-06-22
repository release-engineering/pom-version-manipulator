/*
 * Copyright (c) 2011 Red Hat, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see 
 * <http://www.gnu.org/licenses>.
 */

package com.redhat.rcm.version.model;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;

public class VersionlessProjectKey
    implements Comparable<ProjectKey>, ProjectKey
{
    private final String groupId;

    private final String artifactId;

    public VersionlessProjectKey( final String groupId, final String artifactId )
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    public VersionlessProjectKey( final Dependency dep )
    {
        groupId = dep.getGroupId();
        artifactId = dep.getArtifactId();
    }

    public VersionlessProjectKey( Plugin plugin )
    {
        groupId = plugin.getGroupId();
        artifactId = plugin.getArtifactId();
    }

    public VersionlessProjectKey( MavenProject project )
    {
        groupId = project.getGroupId();
        artifactId = project.getArtifactId();
    }

    public VersionlessProjectKey( Parent parent )
    {
        groupId = parent.getGroupId();
        artifactId = parent.getArtifactId();
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.redhat.rcm.version.model.ProjectKey#getGroupId()
     */
    @Override
    public String getGroupId()
    {
        return groupId;
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.redhat.rcm.version.model.ProjectKey#getArtifactId()
     */
    @Override
    public String getArtifactId()
    {
        return artifactId;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( artifactId == null ) ? 0 : artifactId.hashCode() );
        result = prime * result + ( ( groupId == null ) ? 0 : groupId.hashCode() );
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
        final VersionlessProjectKey other = (VersionlessProjectKey) obj;
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
        return true;
    }

    @Override
    public String toString()
    {
        return getId();
    }

    @Override
    public int compareTo( final ProjectKey other )
    {
        if ( other == null )
        {
            return -1;
        }

        int comp = getGroupId().compareTo( other.getGroupId() );
        if ( comp == 0 )
        {
            comp = getArtifactId().compareTo( other.getArtifactId() );
        }

        return comp;
    }

    public String getId()
    {
        return groupId + ":" + artifactId;
    }

}
