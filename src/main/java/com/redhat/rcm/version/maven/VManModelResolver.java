package com.redhat.rcm.version.maven;

import java.util.List;

import org.apache.maven.mae.project.internal.SimpleModelResolver;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.impl.ArtifactResolver;
import org.sonatype.aether.impl.RemoteRepositoryManager;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.util.DefaultRequestTrace;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

public class VManModelResolver
    implements ModelResolver
{

    private SimpleModelResolver delegate;

    private VManWorkspaceReader workspaceReader;

    public VManModelResolver( final VersionManagerSession session, final Project project,
                              final ArtifactResolver artifactResolver,
                              final RemoteRepositoryManager remoteRepositoryManager )
    {
        final RepositorySystemSession rss = session.getRepositorySystemSession();
        final List<RemoteRepository> repos = session.getRemoteRepositoriesForResolution();

        delegate =
            new SimpleModelResolver( rss, repos, new DefaultRequestTrace( project.getKey() ), artifactResolver,
                                     remoteRepositoryManager );

        if ( session.getWorkspaceReader() != null )
        {
            this.workspaceReader = session.getWorkspaceReader();
        }
        else
        {
            this.workspaceReader = new VManWorkspaceReader( session );
        }
    }

    @Override
    public ModelSource resolveModel( final String groupId, final String artifactId, final String version )
        throws UnresolvableModelException
    {
        final FullProjectKey key = new FullProjectKey( groupId, artifactId, version );
        final Project project = workspaceReader.getSessionProject( key );
        if ( project != null )
        {
            return new ProjectModelSource( project );
        }

        return delegate.resolveModel( groupId, artifactId, version );
    }

    @Override
    public void addRepository( final Repository repository )
        throws InvalidRepositoryException
    {
        //        delegate.addRepository( repository );
    }

    @Override
    public ModelResolver newCopy()
    {
        return this;
    }

}
