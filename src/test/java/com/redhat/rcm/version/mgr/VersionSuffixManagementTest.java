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

package com.redhat.rcm.version.mgr;

import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.apache.commons.io.FileUtils.copyFile;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.model.Model;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.redhat.rcm.version.mgr.inject.ToolchainInjector;
import com.redhat.rcm.version.model.Project;

public class VersionSuffixManagementTest
    extends AbstractVersionManagerTest
{

    private static final String TEST_POMS = "suffix/";

    private static final String TOOLCHAIN_PATH = TEST_POMS + "toolchain-1.0.pom";

    private static final String SUFFIX = "-rebuild-1";

    @Before
    public void setup()
        throws Throwable
    {
        Map<Class<?>, Level> levels = new HashMap<Class<?>, Level>();
        levels.put( ToolchainInjector.class, Level.INFO );

        setupLogging( levels );

        setupDirs();
        setupVersionManager();
    }

    @After
    public void teardown()
    {
        deleteDirs();
        flushLogging();
    }

    public void adjustMultiple_ParentVersions_ChildHasInheritedVersion()
        throws Throwable
    {
        String path = "modules-inheritedVersion/pom.xml";

        Map<FullProjectKey, Project> result =
            adjustMultiple( "Adjust ONLY parent POM when child has inherited version", path );

        FullProjectKey key = new FullProjectKey( "test", "parent", "1" + SUFFIX );
        Project project = result.get( key );

        assertThat( "Parent POM cannot be found in result map: " + key, project, notNullValue() );
        assertThat( "Parent has wrong version!", project.getModel().getVersion(), equalTo( key.getVersion() ) );

        key = new FullProjectKey( "test", "child", "1" + SUFFIX );
        project = result.get( key );

        assertThat( "Child POM was modified!", project, nullValue() );
    }

    @Test
    public void adjustMultiple_BothVersions_ChildHasSeparateVersion()
        throws Throwable
    {
        String path = "modules-separateVersions/pom.xml";

        Map<FullProjectKey, Project> result =
            adjustMultiple( "Adjust both POMs when parent and child have separate versions", path );

        FullProjectKey key = new FullProjectKey( "test", "parent", "1" + SUFFIX );
        Project project = result.get( key );

        assertThat( "Parent POM cannot be found in result map: " + key, project, notNullValue() );
        assertThat( "Parent has wrong version!", project.getModel().getVersion(), equalTo( key.getVersion() ) );

        key = new FullProjectKey( "test", "child", "2" + SUFFIX );
        project = result.get( key );

        assertThat( "Child POM cannot be found in result map: " + key, project, notNullValue() );
        assertParent( project.getModel(), null, null, "1" + SUFFIX, true );
        assertThat( "Child has wrong version!", project.getModel().getVersion(), equalTo( key.getVersion() ) );
    }

    @Test
    public void dontAdjustVersion_InheritedVersion_ToolchainParent()
        throws Throwable
    {
        String path = "child-inheritedVersion-1.0.pom";
        Model original = loadModel( TEST_POMS + path );

        assertParent( original, null, null, "1.0", true );

        final File srcPom = getResourceFile( TEST_POMS + path );
        final String toolchain = getResourceFile( TOOLCHAIN_PATH ).getAbsolutePath();

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        final VersionManagerSession session = new VersionManagerSession( workspace, reports, SUFFIX, false );

        final Set<File> modified = vman.modifyVersions( pom, null, toolchain, null, session );
        assertNoErrors( session );

        Set<Model> changedModels = loadModels( modified );
        assertThat( "POM: " + path + " was modified!", changedModels.size(), equalTo( 0 ) );
    }

    @Test
    public void adjustVersion_SeparateVersion_ToolchainParent()
        throws Throwable
    {
        String path = "child-separateVersion-1.0.1.pom";
        Model original = loadModel( TEST_POMS + path );

        assertThat( "Original POM has wrong version!", original.getVersion(), equalTo( "1.0.1" ) );

        Project project =
            adjustSingle( "Adjust a POM with an independent version that inherits from the toolchain.", path );

        assertThat( "Modified POM has wrong version!", project.getModel().getVersion(), equalTo( "1.0.1" + SUFFIX ) );
    }

    @Test
    public void dontAdjustVersion_InheritedVersion_ChildOfExternalParent_NoToolchainAncestor()
        throws Throwable
    {
        String path = "external-withParent-inheritedVersion-1.0.pom";
        Model original = loadModel( TEST_POMS + path );

        assertParent( original, null, null, "1", true );

        Project project = adjustSingle( "DO NOT adjust a POM that inherits its version from an external parent.", path );

        assertParent( project.getModel(), null, null, "1", true );
    }

    @Test
    public void adjustVersion_SeparateVersion_ChildOfExternalParent_NoToolchainAncestor()
        throws Throwable
    {
        String path = "external-withParent-separateVersion-1.0.pom";
        Model original = loadModel( TEST_POMS + path );

        assertThat( "Original POM has wrong version!", original.getVersion(), equalTo( "1.0" ) );

        Project project =
            adjustSingle( "Adjust a POM with an independent version, a parent NOT in the "
                + "current workspace, and without the toolchain in its ancestry.", path );

        assertThat( "Modified POM has wrong version!", project.getModel().getVersion(), equalTo( "1.0" + SUFFIX ) );
    }

    @Test
    public void adjustVersion_NoParent()
        throws Throwable
    {
        String path = "external-noParent-1.0.pom";
        Model original = loadModel( TEST_POMS + path );

        assertThat( "Original POM has wrong version!", original.getVersion(), equalTo( "1.0" ) );

        Project project = adjustSingle( "Adjust the version of POM without a parent.", path );

        assertThat( "Modified POM has wrong version!", project.getModel().getVersion(), equalTo( "1.0" + SUFFIX ) );
    }

    private Project adjustSingle( final String description, final String pomPath )
        throws Throwable
    {
        try
        {
            System.out.println( "ADJUSTING: " + description + "\nPOM: " + pomPath + "\nToolchain: " + TOOLCHAIN_PATH );

            final File srcPom = getResourceFile( TEST_POMS + pomPath );
            final String toolchain = getResourceFile( TOOLCHAIN_PATH ).getAbsolutePath();

            final File pom = new File( repo, srcPom.getName() );
            copyFile( srcPom, pom );

            final VersionManagerSession session = new VersionManagerSession( workspace, reports, SUFFIX, false );

            File remoteRepo = getResourceFile( TEST_POMS + "repo" );
            session.setRemoteRepository( remoteRepo.toURI().normalize().toURL().toExternalForm() );

            final Set<File> modified = vman.modifyVersions( pom, null, toolchain, null, session );
            assertNoErrors( session );

            Set<Model> changedModels = loadModels( modified );
            assertThat( "POM: " + pomPath + " was not modified!", changedModels.size(), equalTo( 1 ) );

            Model model = changedModels.iterator().next();
            dumpModel( model );

            return new Project( pom, model );
        }
        catch ( Throwable t )
        {
            t.printStackTrace();
            throw t;
        }
    }

    private Map<FullProjectKey, Project> adjustMultiple( final String description, final String pomPath )
        throws Throwable
    {
        try
        {
            System.out.println( "ADJUSTING: " + description + "\nPOM: " + pomPath + "\nToolchain: " + TOOLCHAIN_PATH );

            final File srcPom = getResourceFile( TEST_POMS + pomPath );
            final String toolchain = getResourceFile( TOOLCHAIN_PATH ).getAbsolutePath();

            File dir = srcPom.getParentFile();
            String fname = srcPom.getName();

            final File outDir = new File( repo, dir.getName() ).getAbsoluteFile();
            outDir.mkdirs();

            copyDirectory( dir, outDir );
            File pom = new File( outDir, fname ).getAbsoluteFile();

            final VersionManagerSession session = new VersionManagerSession( workspace, reports, SUFFIX, false );

            File remoteRepo = getResourceFile( TEST_POMS + "repo" );
            session.setRemoteRepository( remoteRepo.toURI().normalize().toURL().toExternalForm() );

            final Set<File> modified = vman.modifyVersions( pom, null, toolchain, null, session );
            assertNoErrors( session );

            Set<Model> changedModels = loadModels( modified );
            assertThat( "POM: " + pomPath + " was not modified!", changedModels.size(), not( equalTo( 0 ) ) );

            Map<FullProjectKey, Project> result = new HashMap<FullProjectKey, Project>();
            for ( Model model : changedModels )
            {
                dumpModel( model );
                Project project = new Project( model );
                result.put( project.getKey(), project );
            }

            return result;
        }
        catch ( Throwable t )
        {
            t.printStackTrace();
            throw t;
        }
    }

    private void assertParent( final Model model, final String groupId, final String artifactId, final String version,
                               final boolean matches )
    {
        assertThat( "Model is null.", model, notNullValue() );

        assertThat( "Parent is null.", model.getParent(), notNullValue() );

        if ( matches )
        {
            if ( groupId != null )
            {
                assertThat( "Parent has wrong groupId.", model.getParent().getGroupId(), equalTo( groupId ) );
            }

            if ( artifactId != null )
            {
                assertThat( "Parent has wrong artifactId.", model.getParent().getArtifactId(), equalTo( artifactId ) );
            }

            if ( version != null )
            {
                assertThat( "Parent has wrong version.", model.getParent().getVersion(), equalTo( version ) );
            }
        }
        else
        {
            if ( groupId != null )
            {
                assertThat( "Parent has wrong groupId.", model.getParent().getGroupId(), not( equalTo( groupId ) ) );
            }

            if ( artifactId != null )
            {
                assertThat( "Parent has wrong artifactId.", model.getParent().getArtifactId(),
                            not( equalTo( artifactId ) ) );
            }

            if ( version != null )
            {
                assertThat( "Parent has wrong version.", model.getParent().getVersion(), not( equalTo( version ) ) );
            }
        }
    }

}
