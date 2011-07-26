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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.project.MavenProject;

import com.redhat.rcm.version.VManException;

public class FullProjectKey
    extends VersionlessProjectKey
{

    private final String version;

    private Dependency bomDep;

    public FullProjectKey( final String groupId, final String artifactId, final String version )
    {
        super( groupId, artifactId );
        this.version = version;
    }

    public FullProjectKey( final Project project )
    {
        this( project.getGroupId(), project.getArtifactId(), project.getVersion() );
    }

    public FullProjectKey( final Parent parent )
    {
        this( parent.getGroupId(), parent.getArtifactId(), parent.getVersion() );
    }

    public FullProjectKey( final Dependency dependency )
    {
        this( dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion() );
    }

    public FullProjectKey( Model model )
        throws VManException
    {
        this( selectGroupId( model ), model.getArtifactId(), selectVersion( model ) );
    }

    public FullProjectKey( MavenProject project )
    {
        this( project.getGroupId(), project.getArtifactId(), project.getVersion() );
    }

    private static String selectVersion( Model model )
        throws VManException
    {
        String version = model.getVersion();
        if ( version == null )
        {
            Parent parent = model.getParent();
            if ( parent != null )
            {
                version = parent.getVersion();
            }
        }
        
        if ( version == null )
        {
            throw new VManException( "Invalid model (missing version): %s", model );
        }
        
        return version;
    }

    private static String selectGroupId( Model model )
        throws VManException
    {
        String gid = model.getGroupId();
        if ( gid == null )
        {
            Parent parent = model.getParent();
            if ( parent != null )
            {
                gid = parent.getGroupId();
            }
        }
        
        if ( gid == null )
        {
            throw new VManException( "Invalid model (missing groupId): %s", model );
        }
        
        return gid;
    }

    public String getVersion()
    {
        return version;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ( ( version == null ) ? 0 : version.hashCode() );
        return result;
    }

    @Override
    public boolean equals( final Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( !super.equals( obj ) )
        {
            return false;
        }
        if ( getClass() != obj.getClass() )
        {
            return false;
        }
        final FullProjectKey other = (FullProjectKey) obj;
        if ( version == null )
        {
            if ( other.version != null )
            {
                return false;
            }
        }
        else if ( !version.equals( other.version ) )
        {
            return false;
        }
        return true;
    }

    @Override
    public String toString()
    {
        return super.getId() + ":" + version;
    }
    
    public synchronized Dependency getBomDependency()
    {
        if ( bomDep == null )
        {
            bomDep = new Dependency();
            bomDep.setGroupId( getGroupId() );
            bomDep.setArtifactId( getArtifactId() );
            bomDep.setVersion( version );
            bomDep.setType( "pom" );
            bomDep.setScope( Artifact.SCOPE_IMPORT );
        }

        return bomDep;
    }

}
