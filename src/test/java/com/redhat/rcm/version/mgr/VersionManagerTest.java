package com.redhat.rcm.version.mgr;

import static com.redhat.rcm.version.testutil.TestProjectFixture.getResourceFile;
import static org.apache.commons.lang.StringUtils.join;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.mae.project.key.FullProjectKey;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.testutil.TestProjectFixture;

public class VersionManagerTest
    extends AbstractVersionManagerTest
{

    private static final String BASE = "pom-loading/";

    @Rule
    public TestProjectFixture fix = new TestProjectFixture();

    @Before
    public void setup()
        throws Exception
    {
        setupVersionManager();
    }

    @Test( timeout = 500 )
    public void avoidDupeWhenHasChildExplicitRelativeParent()
        throws Exception
    {
        final long start = System.currentTimeMillis();
        final VersionManagerSession session = createVersionManagerSession();
        final File pom = getResourceFile( BASE + "deloop-parent/pom.xml" );
        final LinkedHashSet<Project> projects = vman.loadProjectWithModules( pom, session );

        System.out.printf( "Projects:\n\n  %s\n", join( projects, "\n  " ) );

        assertThat( projects, notNullValue() );
        assertThat( projects.size(), equalTo( 2 ) );

        final Set<FullProjectKey> keys = new HashSet<FullProjectKey>();
        for ( final Project project : projects )
        {
            keys.add( project.getKey() );
        }

        assertThat( keys.contains( new FullProjectKey( "test", "deloop-parent", "1" ) ), equalTo( true ) );
        assertThat( keys.contains( new FullProjectKey( "test", "deloop-child", "1" ) ), equalTo( true ) );

        System.out.printf( "Elapsed: %d ms\n", ( System.currentTimeMillis() - start ) );
    }

    @Test
    public void loadParentFromSubModulesDir()
        throws Exception
    {
        final VersionManagerSession session = createVersionManagerSession();
        final File pom = getResourceFile( BASE + "parent-in-subdir/pom.xml" );
        final LinkedHashSet<Project> projects = vman.loadProjectWithModules( pom, session );

        System.out.printf( "Projects:\n\n  %s\n", join( projects, "\n  " ) );

        assertThat( projects, notNullValue() );
        assertThat( projects.size(), equalTo( 3 ) );

        final Set<FullProjectKey> keys = new HashSet<FullProjectKey>();
        for ( final Project project : projects )
        {
            keys.add( project.getKey() );
        }

        assertThat( keys.contains( new FullProjectKey( "test", "parent-in-subdir", "1" ) ), equalTo( true ) );
        assertThat( keys.contains( new FullProjectKey( "test", "parent-in-subdir-parent", "1" ) ), equalTo( true ) );
        assertThat( keys.contains( new FullProjectKey( "test", "parent-in-subdir-child", "1" ) ), equalTo( true ) );
    }

    @Test
    public void loadParentViaRelativePathFromSubDir()
        throws Exception
    {
        final VersionManagerSession session = createVersionManagerSession();
        final File pom = getResourceFile( BASE + "parent-in-relpath/pom.xml" );
        final LinkedHashSet<Project> projects = vman.loadProjectWithModules( pom, session );

        System.out.printf( "Projects:\n\n  %s\n", join( projects, "\n  " ) );

        assertThat( projects, notNullValue() );
        assertThat( projects.size(), equalTo( 3 ) );

        final Set<FullProjectKey> keys = new HashSet<FullProjectKey>();
        for ( final Project project : projects )
        {
            keys.add( project.getKey() );
        }

        assertThat( keys.contains( new FullProjectKey( "test", "parent-in-relpath", "1" ) ), equalTo( true ) );
        assertThat( keys.contains( new FullProjectKey( "test", "parent-in-relpath-parent", "1" ) ), equalTo( true ) );
        assertThat( keys.contains( new FullProjectKey( "test", "parent-in-relpath-child", "1" ) ), equalTo( true ) );
    }

}
