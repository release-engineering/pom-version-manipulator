/*
 * Copyright (c) 2010 Red Hat, Inc.
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

import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.InputLocation;

public final class ReadOnlyDependency
    extends Dependency
{
    private static final long serialVersionUID = 1L;

    private final Dependency dep;

    public ReadOnlyDependency( final Dependency dep )
    {
        this.dep = dep.clone();
    }

    @Override
    public String getArtifactId()
    {
        return dep.getArtifactId();
    }

    @Override
    public String getClassifier()
    {
        return dep.getClassifier();
    }

    @Override
    public List<Exclusion> getExclusions()
    {
        return dep.getExclusions();
    }

    @Override
    public String getGroupId()
    {
        return dep.getGroupId();
    }

    @Override
    public InputLocation getLocation( final Object key )
    {
        return dep.getLocation( key );
    }

    @Override
    public String getOptional()
    {
        return dep.getOptional();
    }

    @Override
    public String getScope()
    {
        return dep.getScope();
    }

    @Override
    public String getSystemPath()
    {
        return dep.getSystemPath();
    }

    @Override
    public String getType()
    {
        return dep.getType();
    }

    @Override
    public String getVersion()
    {
        return dep.getVersion();
    }

    @Override
    public boolean isOptional()
    {
        return dep.isOptional();
    }

    @Override
    public String getManagementKey()
    {
        return dep.getManagementKey();
    }

    @Override
    public void addExclusion( final Exclusion exclusion )
    {
        throw new UnsupportedOperationException( "Immutable dependency instance." );
    }

    @Override
    public Dependency clone()
    {
        return new ReadOnlyDependency( dep.clone() );
    }

    @Override
    public void removeExclusion( final Exclusion exclusion )
    {
        throw new UnsupportedOperationException( "Immutable dependency instance." );
    }

    @Override
    public void setArtifactId( final String artifactId )
    {
        throw new UnsupportedOperationException( "Immutable dependency instance." );
    }

    @Override
    public void setClassifier( final String classifier )
    {
        throw new UnsupportedOperationException( "Immutable dependency instance." );
    }

    @Override
    public void setExclusions( final List<Exclusion> exclusions )
    {
        throw new UnsupportedOperationException( "Immutable dependency instance." );
    }

    @Override
    public void setGroupId( final String groupId )
    {
        throw new UnsupportedOperationException( "Immutable dependency instance." );
    }

    @Override
    public void setLocation( final Object key, final InputLocation location )
    {
        throw new UnsupportedOperationException( "Immutable dependency instance." );
    }

    @Override
    public void setOptional( final String optional )
    {
        throw new UnsupportedOperationException( "Immutable dependency instance." );
    }

    @Override
    public void setScope( final String scope )
    {
        throw new UnsupportedOperationException( "Immutable dependency instance." );
    }

    @Override
    public void setSystemPath( final String systemPath )
    {
        throw new UnsupportedOperationException( "Immutable dependency instance." );
    }

    @Override
    public void setType( final String type )
    {
        throw new UnsupportedOperationException( "Immutable dependency instance." );
    }

    @Override
    public void setVersion( final String version )
    {
        throw new UnsupportedOperationException( "Immutable dependency instance." );
    }

    @Override
    public void setOptional( final boolean optional )
    {
        throw new UnsupportedOperationException( "Immutable dependency instance." );
    }

    @Override
    public String toString()
    {
        return dep.toString();
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( dep.getGroupId() == null ) ? 0 : dep.getGroupId().hashCode() );
        result = prime * result + ( ( dep.getArtifactId() == null ) ? 0 : dep.getArtifactId().hashCode() );
        result = prime * result + ( ( dep.getVersion() == null ) ? 0 : dep.getVersion().hashCode() );
        result = prime * result + ( ( dep.getType() == null ) ? 0 : dep.getType().hashCode() );
        result = prime * result + ( ( dep.getClassifier() == null ) ? 0 : dep.getClassifier().hashCode() );
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
        ReadOnlyDependency other = (ReadOnlyDependency) obj;
        String[] values =
            { dep.getGroupId(), dep.getArtifactId(), dep.getVersion(), dep.getType(), dep.getClassifier() };

        String[] otherValues =
            { other.getGroupId(), other.getArtifactId(), other.getVersion(), other.getType(), other.getClassifier() };

        for ( int i = 0; i < values.length; i++ )
        {
            if ( values[i] == null )
            {
                if ( otherValues[i] != null )
                {
                    return false;
                }
            }
            else if ( !values[i].equals( otherValues[i] ) )
            {
                return false;
            }
        }

        return true;
    }

}
