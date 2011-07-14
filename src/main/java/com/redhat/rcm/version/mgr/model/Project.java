/*
 *  Copyright (C) 2011 John Casey.
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.redhat.rcm.version.mgr.model;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.model.FullProjectKey;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class Project
{
    
    private final File pom;
    
    private final Model model;
    
    private final FullProjectKey key;
    
    public Project( File pom, Model model )
        throws VManException
    {
        this.pom = pom;
        this.model = model;
        this.key = new FullProjectKey( model );
    }

    public File getPom()
    {
        return pom;
    }

    public Model getModel()
    {
        return model;
    }
    
    public FullProjectKey getKey()
    {
        return key;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( key == null ) ? 0 : key.hashCode() );
        return result;
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        Project other = (Project) obj;
        if ( key == null )
        {
            if ( other.key != null )
                return false;
        }
        else if ( !key.equals( other.key ) )
            return false;
        return true;
    }

    @Override
    public String toString()
    {
        return key + " [pom=" + pom + "]";
    }

    public Parent getParent()
    {
        return model.getParent();
    }

    public String getGroupId()
    {
        return key.getGroupId();
    }

    public String getArtifactId()
    {
        return key.getArtifactId();
    }

    public String getVersion()
    {
        return key.getVersion();
    }

    public List<Plugin> getBuildPlugins()
    {
        Build build = model.getBuild();
        if ( build == null )
        {
            return Collections.emptyList();
        }
        
        List<Plugin> result = build.getPlugins();
        if( result == null )
        {
            return Collections.emptyList();
        }
        
        return result;
    }

}
