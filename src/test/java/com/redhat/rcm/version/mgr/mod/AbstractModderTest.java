package com.redhat.rcm.version.mgr.mod;

import java.io.File;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

import com.redhat.rcm.version.fixture.LoggingFixture;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.testutil.TestVersionManager;

public class AbstractModderTest
{

    protected File workspace;

    protected File reports;

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public TestName name = new TestName();

    protected AbstractModderTest()
    {
    }

    @BeforeClass
    public static void setupLogging()
    {
        LoggingFixture.setupLogging();
    }

    @Before
    public final void setupDirs()
        throws Exception
    {
        System.out.println( "START: " + name.getMethodName() + "\n\n" );

        if ( workspace == null )
        {
            workspace = tempFolder.newFolder( "workspace" );
        }

        if ( reports == null )
        {
            reports = tempFolder.newFolder( "reports" );
        }

        setupComplete();
    }

    protected void setupComplete()
        throws Exception
    {
    }

    @After
    public final void logTestNameEnd()
    {
        System.out.println( "\n\nEND: " + name.getMethodName() );
    }

    protected void configureSession( final List<String> boms, final String toolchain,
                                     final VersionManagerSession session )
        throws Exception
    {
        TestVersionManager.getInstance()
                          .configureSession( boms, toolchain, session );
    }

}