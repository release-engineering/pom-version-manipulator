package com.redhat.rcm.version.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.WorkspaceReader;
import org.sonatype.aether.repository.WorkspaceRepository;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

public class VManWorkspaceReader
    implements WorkspaceReader
{

    private final WorkspaceRepository repo = new WorkspaceRepository( "vman", "vman" );

    private VersionManagerSession session;

    public VManWorkspaceReader( final VersionManagerSession session )
    {
        this.session = session;
    }

    @Override
    public WorkspaceRepository getRepository()
    {
        return repo;
    }

    @Override
    public File findArtifact( final Artifact artifact )
    {
        if ( !"pom".equals( artifact.getExtension() ) )
        {
            return null;
        }

        final Project project =
            session.getCurrentProject( new VersionlessProjectKey( artifact.getGroupId(), artifact.getArtifactId() ) );
        return project == null ? null : project.getPom();
    }

    @Override
    public List<String> findVersions( final Artifact artifact )
    {
        final List<String> versions = new ArrayList<String>( 1 );
        final Project project =
            session.getCurrentProject( new VersionlessProjectKey( artifact.getGroupId(), artifact.getArtifactId() ) );
        if ( project != null )
        {
            versions.add( project.getVersion() );
        }

        return versions;
    }

}
