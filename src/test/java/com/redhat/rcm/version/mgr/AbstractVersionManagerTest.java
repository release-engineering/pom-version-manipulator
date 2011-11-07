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

package com.redhat.rcm.version.mgr;

import static com.redhat.rcm.version.testutil.TestProjectUtils.getResourceFile;
import static junit.framework.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.mae.MAEException;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;

public abstract class AbstractVersionManagerTest
{

    protected static final String TOOLCHAIN = "toolchain/toolchain-1.0.pom";

    protected VersionManager vman;

    protected File repo;

    protected File workspace;

    protected File reports;

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    protected AbstractVersionManagerTest()
    {
    }

    @BeforeClass
    public static void enableClasspathScanning()
    {
        System.out.println( "Enabling classpath scanning..." );
        VersionManager.setClasspathScanning( true );
    }

    public void setupVersionManager()
        throws MAEException
    {
        if ( vman == null )
        {
            vman = VersionManager.getInstance();
        }
    }

    public synchronized void setupDirs()
        throws IOException
    {
        if ( repo == null )
        {
            repo = tempFolder.newFolder( "repository" );
        }

        if ( workspace == null )
        {
            workspace = tempFolder.newFolder( "workspace" );
        }

        if ( reports == null )
        {
            reports = tempFolder.newFolder( "reports" );
        }
    }

    protected void assertNoErrors( final VersionManagerSession session )
    {
        List<Throwable> errors = session.getErrors();
        if ( errors != null && !errors.isEmpty() )
        {
            StringBuilder sb = new StringBuilder();
            sb.append( errors.size() ).append( "errors encountered\n\n" );

            int idx = 1;
            for ( Throwable error : errors )
            {
                StringWriter sw = new StringWriter();
                error.printStackTrace( new PrintWriter( sw ) );

                sb.append( "\n" ).append( idx ).append( ".  " ).append( sw.toString() );
                idx++;
            }

            sb.append( "\n\nSee above errors." );

            fail( sb.toString() );
        }
    }

    protected String getToolchainPath()
    {
        return getResourceFile( TOOLCHAIN ).getAbsolutePath();
    }

    protected VersionManagerSession modifyRepo( final boolean useToolchain, final String... boms )
    {
        final VersionManagerSession session = newVersionManagerSession();

        vman.modifyVersions( repo,
                             "**/*.pom",
                             Arrays.asList( boms ),
                             useToolchain ? getToolchainPath() : null,
                             null,
                             session );
        assertNoErrors( session );
        vman.generateReports( reports, session );

        return session;
    }

    protected VersionManagerSession newVersionManagerSession()
    {
        return new VersionManagerSession( workspace, reports, null, false );
    }

}