package com.redhat.rcm.version.util;

import static com.redhat.rcm.version.testutil.TestProjectUtils.getResourceFile;
import static com.redhat.rcm.version.testutil.TestProjectUtils.loadModel;
import static com.redhat.rcm.version.util.PomUtils.writeModifiedPom;
import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.List;

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

import com.redhat.rcm.version.mgr.VersionManagerSession;

public class PomUtilsTest
{

    private static final String BASE = "pom-formats/";

    // private static final String TOOLCHAIN = BASE + "toolchain.pom";

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public final TestName testName = new TestName();

    private File reports;

    private File workspace;

    private VersionManagerSession session;

    @Before
    public void setup()
        throws Exception
    {
        workspace = tempFolder.newFolder( "workspace" );
        reports = tempFolder.newFolder( "reports" );

        // File toolchainPom = getResourceFile( TOOLCHAIN );
        // Model toolchainModel = loadModel( toolchainPom );
        // MavenProject toolchainProject = new MavenProject( toolchainModel );

        session = new VersionManagerSession( workspace, reports, null, false );
        // session.setToolchain( toolchainPom, toolchainProject );
    }

    @Test
    public void pomRewritePreservesXMLAttributesInPluginConfiguration()
        throws Exception
    {
        File pom = getResourceFile( BASE + "plugin-config-attributes.pom" );
        File temp = tempFolder.newFile( pom.getName() );
        copyFile( pom, temp );
        pom = temp;

        Model model = loadModel( pom );

        assertThat( model.getDependencies(), notNullValue() );
        assertThat( model.getDependencies().size(), equalTo( 1 ) );
        for ( Dependency dep : model.getDependencies() )
        {
            System.out.println( "Verifying starting condition for dep: " + dep );
            assertThat( dep.getVersion(), notNullValue() );
        }

        assertThat( model.getBuild(), notNullValue() );

        List<Plugin> plugins = model.getBuild().getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );

        Plugin plugin = plugins.get( 0 );
        Object config = plugin.getConfiguration();
        assertThat( config, notNullValue() );

        assertThat( config.toString().contains( "<delete dir=\"foobar\"" ), equalTo( true ) );

        model.getDependencies().get( 0 ).setVersion( null );
        plugin.setVersion( null );

        VersionlessProjectKey coord = new VersionlessProjectKey( model.getGroupId(), model.getArtifactId() );

        File basedir = tempFolder.newFolder( testName.getMethodName() + ".out.dir" );
        File out = writeModifiedPom( model, pom, coord, model.getVersion(), basedir, session, false );

        String pomStr = readFileToString( out );
        System.out.println( "Modified POM for " + testName.getMethodName() + ":\n\n" + pomStr + "\n\n" );

        Model changed = loadModel( out );

        assertThat( changed.getDependencies(), notNullValue() );
        assertThat( changed.getDependencies().size(), equalTo( 1 ) );
        for ( Dependency dep : changed.getDependencies() )
        {
            assertThat( dep.getVersion(), nullValue() );
        }

        assertThat( changed.getBuild(), notNullValue() );

        plugins = changed.getBuild().getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );

        plugin = plugins.get( 0 );
        config = plugin.getConfiguration();
        assertThat( config, notNullValue() );
        assertThat( config.toString().contains( "<delete dir=\"foobar\"" ), equalTo( true ) );
    }

}
