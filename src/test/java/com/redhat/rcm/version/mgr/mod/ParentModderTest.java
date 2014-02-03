package com.redhat.rcm.version.mgr.mod;

import static com.redhat.rcm.version.testutil.TestProjectFixture.dumpModel;
import static com.redhat.rcm.version.testutil.TestProjectFixture.loadModel;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.File;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import com.redhat.rcm.version.mgr.AbstractVersionManagerTest;
import com.redhat.rcm.version.mgr.session.SessionBuilder;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

public class ParentModderTest
    extends AbstractVersionManagerTest
{

    private static final String TOOLCHAIN_TEST_POMS = "toolchain/";

    private static final String EMPTY_TOOLCHAIN_PATH = TOOLCHAIN_TEST_POMS + "toolchain-empty-1.0.pom";

    @Test
    public void relocateParent()
        throws Throwable
    {
        final String path = "relocate-parent.pom";
        final Model original = loadModel( TOOLCHAIN_TEST_POMS + path );

        final String toolchainPath = EMPTY_TOOLCHAIN_PATH;
        final Model toolchainModel = loadModel( toolchainPath );
        final MavenProject toolchainProject = new MavenProject( toolchainModel );
        toolchainProject.setOriginalModel( toolchainModel );

        Parent parent = original.getParent();
        assertThat( parent, notNullValue() );
        assertThat( parent.getArtifactId(), equalTo( "old-parent" ) );
        assertThat( parent.getVersion(), equalTo( "1.0" ) );

        final Project project = new Project( original );
        final SessionBuilder builder =
            new SessionBuilder( workspace, reports ).withCoordinateRelocation( "org.test:old-parent:1.0", "org.test:new-parent:2.0" );

        final VersionManagerSession session = builder.build();
        session.setToolchain( new File( toolchainPath ), toolchainProject );

        final boolean changed = new ParentModder().inject( project, session );

        dumpModel( project.getModel() );

        assertThat( changed, equalTo( true ) );
        assertNoErrors( session );

        parent = project.getModel()
                        .getParent();
        assertThat( parent, notNullValue() );
        assertThat( parent.getArtifactId(), equalTo( "new-parent" ) );
        assertThat( parent.getVersion(), equalTo( "2.0" ) );
    }

}
