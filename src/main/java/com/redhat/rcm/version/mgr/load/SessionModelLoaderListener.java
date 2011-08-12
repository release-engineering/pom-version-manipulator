package com.redhat.rcm.version.mgr.load;

import org.apache.maven.mae.project.ModelLoader;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.event.ModelLoaderEvent;
import org.apache.maven.mae.project.event.ModelLoaderEventType;
import org.apache.maven.mae.project.event.ModelLoaderListener;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.sonatype.aether.RequestTrace;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

public class SessionModelLoaderListener
    implements ModelLoaderListener
{

    private final VersionManagerSession session;

    private final ModelLoader modelLoader;

    public SessionModelLoaderListener( final VersionManagerSession session, final ModelLoader modelLoader )
    {
        this.session = session;
        this.modelLoader = modelLoader;
    }

    @Override
    public void onEvent( final ModelLoaderEvent event )
        throws ProjectToolsException
    {
        if ( event.getType() == ModelLoaderEventType.BUILT )
        {
            Project project = new Project( event.getKey(), event.getPom(), event.getModel() );
            reconstituteParent( project, event.getTrace(), event.getTrace() );

            session.connectProject( project );
        }
    }

    private void reconstituteParent( final Project project, final RequestTrace originalTrace, final RequestTrace trace )
        throws VManException
    {
        Parent parent = project.getParent();

        if ( parent != null )
        {
            FullProjectKey parentKey = new FullProjectKey( parent );
            String suffix = session.getVersionSuffix();
            if ( suffix != null && !parent.getVersion().endsWith( suffix ) )
            {
                parentKey = new FullProjectKey( parentKey, parent.getVersion() + suffix );
            }

            if ( !session.ancestryGraphContains( parentKey ) )
            {
                RequestTrace parentTrace = trace.newChild( parentKey );
                Model parentModel;
                try
                {
                    parentModel = modelLoader.loadRawModel( parentKey, parentTrace, session );
                }
                catch ( ProjectToolsException e )
                {
                    throw new VManException( "Failed to recontruct ancestry for %s. Reason: %s", e, originalTrace,
                                             e.getMessage() );
                }

                Project parentProject = new Project( parentKey, parentModel.getPomFile(), parentModel );

                // recurse, as long as our parent isn't in the ancestry graph.
                reconstituteParent( parentProject, originalTrace, parentTrace );

                session.connectProject( parentProject );
            }
        }
    }

}
