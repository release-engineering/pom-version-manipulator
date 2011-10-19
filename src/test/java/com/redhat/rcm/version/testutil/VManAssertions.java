package com.redhat.rcm.version.testutil;

import static com.redhat.rcm.version.testutil.TestProjectUtils.loadModel;
import static com.redhat.rcm.version.testutil.TestProjectUtils.loadProjectKey;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;

public final class VManAssertions
{

    private VManAssertions()
    {
    }

    public static void assertNormalizedToBOMs( final Set<File> modified, final Set<File> boms )
        throws Exception
    {
        assertNotNull( modified );

        Set<FullProjectKey> bomKeys = new HashSet<FullProjectKey>();
        for ( File bom : boms )
        {
            bomKeys.add( loadProjectKey( bom ) );
        }

        for ( final File out : modified )
        {
            System.out.println( "Examining: " + out );

            Model model = loadModel( out );

            // NOTE: Assuming injection of BOMs will happen in toolchain ancestor now...
            //
            // final DependencyManagement dm = model.getDependencyManagement();
            // if ( dm != null )
            // {
            // Set<FullProjectKey> foundBoms = new HashSet<FullProjectKey>();
            //
            // for ( final Dependency dep : dm.getDependencies() )
            // {
            // if ( ( "pom".equals( dep.getType() ) && Artifact.SCOPE_IMPORT.equals( dep.getScope() ) ) )
            // {
            // foundBoms.add( new FullProjectKey( dep ) );
            // }
            // else
            // {
            // assertNull( "Managed Dependency version was NOT nullified: " + dep.getManagementKey()
            // + "\nPOM: " + out, dep.getVersion() );
            // }
            // }
            //
            // assertThat( foundBoms, equalTo( bomKeys ) );
            // }

            for ( final Dependency dep : model.getDependencies() )
            {
                if ( !( "pom".equals( dep.getType() ) && Artifact.SCOPE_IMPORT.equals( dep.getScope() ) ) )
                {
                    assertNull( "Dependency version was NOT nullified: " + dep.getManagementKey() + "\nPOM: " + out,
                                dep.getVersion() );
                }
            }
        }
    }
}
