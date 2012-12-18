package com.redhat.rcm.version.maven;

import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.WriterFactory;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.WorkspaceReader;
import org.sonatype.aether.repository.WorkspaceRepository;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

public class VManWorkspaceReader
    implements WorkspaceReader
{

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
            final Project project = getSessionProject( key );

            if ( project != null )
            {
                final Model m = project.getOriginalModel();
                Writer writer = null;
                try
                {
                    f = File.createTempFile( m.getArtifactId() + "-VMAN.", ".pom" );

                    writer = WriterFactory.newXmlWriter( f );
                    new MavenXpp3Writer().write( writer, m );

                    projectFiles.put( key, f );
                }
                catch ( final IOException e )
                {
                    session.addError( new VManException(
                                                         "Failed to create temporary file for in-memory POM: %s. Reason: %s",
                                                         e, m, e.getMessage() ) );
                }
                finally
                {
                    closeQuietly( writer );
                }
            }
        }

        return f;
    }

    public Project getSessionProject( final FullProjectKey key )
    {
        Project project = session.getCurrentProject( key );
        if ( project == null && key.equals( session.getToolchainKey() ) )
        {
            final MavenProject p = session.getToolchainProject();
            project = new Project( key, p.getFile(), p.getOriginalModel(), p.getOriginalModel() );
        }

        if ( project == null && session.isBom( key ) )
        {
            final MavenProject p = session.getBOMProject( key );
            project = new Project( key, p.getFile(), p.getOriginalModel(), p.getOriginalModel() );
        }

        return project;
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
