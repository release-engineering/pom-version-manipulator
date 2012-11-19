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

import org.apache.maven.mae.MAEException;
import org.apache.maven.mae.app.AbstractMAEApplication;
import org.apache.maven.mae.boot.embed.MAEEmbedderBuilder;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import com.redhat.rcm.version.config.SessionConfigurator;
import com.redhat.rcm.version.mgr.VersionManager;

@Component( role = TestApp.class )
public class TestApp
    extends AbstractMAEApplication
{

    private static Object lock = new Object();

    private static TestApp instance;

    @Requirement
    private VersionManager vman;

    @Requirement
    private SessionConfigurator configurator;

    private static boolean classpathScanning;

    public static TestApp getInstance()
        throws MAEException
    {
        synchronized ( lock )
        {
            if ( instance == null )
            {
                instance = new TestApp();
                instance.load();
            }
        }

        return instance;
    }

    @Override
    public String getId()
    {
        return getName();
    }

    @Override
    public String getName()
    {
        return "VMan-Test";
    }

    public VersionManager getVman()
    {
        return vman;
    }

    public SessionConfigurator getConfigurator()
    {
        return configurator;
    }

    public static void setClasspathScanning( final boolean scanning )
    {
        classpathScanning = scanning;
    }

    @Override
    protected void configureBuilder( final MAEEmbedderBuilder builder )
        throws MAEException
    {
        super.configureBuilder( builder );
        builder.withClassScanningEnabled( classpathScanning );
    }

}
