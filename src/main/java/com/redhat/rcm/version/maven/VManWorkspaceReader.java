package com.redhat.rcm.version.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.WorkspaceReader;
import org.sonatype.aether.repository.WorkspaceRepository;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

public class VManWorkspaceReader
    implements WorkspaceReader
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final WorkspaceRepository repo = new WorkspaceRepository( "vman", "vman" );

    private final VersionManagerSession session;

    private final Map<FullProjectKey, File> projectFiles = new HashMap<FullProjectKey, File>();

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
    public synchronized File findArtifact( final Artifact artifact )
    {
        if ( !"pom".equals( artifact.getExtension() ) )
        {
            return null;
        }

        final FullProjectKey key =
            new FullProjectKey( artifact.getArtifactId(), artifact.getGroupId(), artifact.getVersion() );

        File f = projectFiles.get( key );
        if ( f == null )
        {
            f = getSessionPOM( key );

            if ( f != null )
            {
                projectFiles.put( key, f );
            }
        }

        return f;
    }

    public File getSessionPOM( final FullProjectKey key )
    {
        File pom = session.getPeekedPom( key );
        logger.info( "Peeked file for key: '{}' is: {}", key, pom );

        if ( pom == null && key.equals( session.getToolchainKey() ) )
        {
            final MavenProject p = session.getToolchainProject();
            pom = p.getFile();
        }

        if ( pom == null && session.isBom( key ) )
        {
            final MavenProject p = session.getBOMProject( key );
            pom = p.getFile();
        }

        return pom;
    }

    @Override
    public List<String> findVersions( final Artifact artifact )
    {
        final List<String> versions = new ArrayList<String>( 1 );

        final VersionlessProjectKey vpk = new VersionlessProjectKey( artifact.getGroupId(), artifact.getArtifactId() );

        final Project project = session.getCurrentProject( vpk );
        if ( project != null )
        {
            versions.add( project.getVersion() );
        }
        else if ( vpk.equals( session.getToolchainKey() ) )
        {
            versions.add( session.getToolchainKey()
                                 .getVersion() );
        }
        else
        {
            final List<FullProjectKey> bomCoords = session.getBomCoords();
            for ( final FullProjectKey bomCoord : bomCoords )
            {
                if ( vpk.equals( bomCoord ) )
                {
                    versions.add( bomCoord.getVersion() );
                    break;
                }
            }
        }

        return versions;
    }

}
