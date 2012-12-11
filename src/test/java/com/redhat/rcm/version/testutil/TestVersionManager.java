/*
 *  Copyright (C) 2012 John Casey.
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

package com.redhat.rcm.version.testutil;

import java.util.List;
import java.util.Map;

import org.apache.maven.mae.MAEException;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.VersionManager;
import com.redhat.rcm.version.mgr.mod.ProjectModder;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

@Component( role = TestVersionManager.class )
public class TestVersionManager
    extends VersionManager
{

    private static Object lock = new Object();

    private static TestVersionManager instance;

    public static TestVersionManager getInstance()
        throws MAEException
    {
        synchronized ( lock )
        {
            if ( instance == null )
            {
                instance = new TestVersionManager();
                instance.load();
            }
        }

        return instance;
    }

    @Override
    public void configureSession( final List<String> boms, final String toolchain, final VersionManagerSession session )
        throws VManException
    {
        super.configureSession( boms, toolchain, session );
    }

    public <T extends ProjectModder> T getModder( final Class<T> type )
    {
        final Map<String, ProjectModder> modders = getModders();
        for ( final ProjectModder modder : modders.values() )
        {
            if ( type.isInstance( modder ) )
            {
                return type.cast( modder );
            }
        }

        return null;
    }

}
