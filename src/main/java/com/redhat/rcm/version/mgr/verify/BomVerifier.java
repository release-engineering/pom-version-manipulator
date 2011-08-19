package com.redhat.rcm.version.mgr.verify;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.VersionManagerSession;
import com.redhat.rcm.version.model.Project;
import com.redhat.rcm.version.util.CollectionToString;
import com.redhat.rcm.version.util.DependencyToString;

@Component( role = ProjectVerifier.class, hint = "BOM-realignment" )
public class BomVerifier
    implements ProjectVerifier
{

    @Override
    public void verify( final Project project, final VersionManagerSession session )
    {
        List<Dependency> missing = new ArrayList<Dependency>();
        for ( Dependency dep : project.getDependencies() )
        {
            if ( isNotEmpty( dep.getVersion() ) )
            {
                missing.add( dep );
            }
        }

        if ( !missing.isEmpty() )
        {
            session.addError( new VManException(
                                                 "The following dependencies were NOT managed.\nProject: %s\nFile: %s\nDependencies:\n\n%s\n",
                                                 project.getKey(), project.getPom(),
                                                 new CollectionToString<Dependency>( missing, new DependencyToString() ) ) );
        }

        List<Dependency> missingManaged = new ArrayList<Dependency>();
        for ( Dependency dep : project.getManagedDependencies() )
        {
            if ( ( !Artifact.SCOPE_IMPORT.equals( dep.getScope() ) || !"pom".equals( dep.getType() ) )
                && isNotEmpty( dep.getVersion() ) )
            {
                missingManaged.add( dep );
            }
        }

        if ( !missingManaged.isEmpty() )
        {
            session.addError( new VManException(
                                                 "The following managed dependencies are NOT BOM references, and were NOT managed.\nProject: %s\nFile: %s\nDependencies:\n\n%s\n",
                                                 project.getKey(), project.getPom(),
                                                 new CollectionToString<Dependency>( missingManaged,
                                                                                     new DependencyToString() ) ) );
        }
    }

}
