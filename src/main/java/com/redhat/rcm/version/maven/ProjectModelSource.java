package com.redhat.rcm.version.maven;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

import com.redhat.rcm.version.model.Project;

public class ProjectModelSource
    implements ModelSource
{

    private Project project;

    public ProjectModelSource( final Project project )
    {
        this.project = project;
    }

    @Override
    public InputStream getInputStream()
        throws IOException
    {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new MavenXpp3Writer().write( baos, project.getModel() );

        return new ByteArrayInputStream( baos.toByteArray() );
    }

    @Override
    public String getLocation()
    {
        return project.getKey()
                      .toString();
    }

}
