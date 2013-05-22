/*
 * Copyright (c) 2010 Red Hat, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see 
 * <http://www.gnu.org/licenses>.
 */

package com.redhat.rcm.version.report;

import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Parent;
import org.codehaus.plexus.component.annotations.Component;
import org.jdom.Comment;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Text;
import org.jdom.output.Format;
import org.jdom.output.Format.TextMode;
import org.jdom.output.XMLOutputter;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = Report.class, hint = MissingParentsReport.ID )
public class MissingParentsReport
    extends AbstractReport
{

    public static final String ID = "missing-parents.xml";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public void generate( final File reportsDir, final VersionManagerSession session )
        throws VManException
    {
        final Set<Project> projectsWithMissingParent = session.getProjectsWithMissingParent();
        if ( projectsWithMissingParent.isEmpty() )
        {
            return;
        }

        final Map<VersionlessProjectKey, Set<Project>> projectsByParent =
            new HashMap<VersionlessProjectKey, Set<Project>>();
        for ( final Project project : projectsWithMissingParent )
        {
            final VersionlessProjectKey parentKey = new VersionlessProjectKey( project.getParent() );
            Set<Project> projects = projectsByParent.get( parentKey );
            if ( projects == null )
            {
                projects = new HashSet<Project>();
                projectsByParent.put( parentKey, projects );
            }

            projects.add( project );
        }

        final Element parents = new Element( "missing-parents" );
        for ( final Map.Entry<VersionlessProjectKey, Set<Project>> parentEntry : projectsByParent.entrySet() )
        {
            if ( parents.getContentSize() > 0 )
            {
                parents.addContent( new Text( "\n\n" ) );
            }

            parents.addContent( new Comment( "START: Parent " + parentEntry.getKey() ) );
            boolean first = true;
            for ( final Project project : parentEntry.getValue() )
            {
                if ( first )
                {
                    first = false;
                }
                else
                {
                    parents.addContent( new Text( "\n\n" ) );
                }

                parents.addContent( new Comment( "In: " + project.getKey() ) );

                final Parent parent = project.getParent();
                final Element p = new Element( "parent" );
                parents.addContent( p );

                p.addContent( new Element( "groupId" ).setText( parent.getGroupId() ) );
                p.addContent( new Element( "artifactId" ).setText( parent.getArtifactId() ) );
                p.addContent( new Element( "version" ).setText( parent.getVersion() ) );
            }
        }

        final Document doc = new Document( parents );

        final Format fmt = Format.getPrettyFormat();
        fmt.setIndent( "  " );
        fmt.setTextMode( TextMode.PRESERVE );

        final XMLOutputter output = new XMLOutputter( fmt );

        final File report = new File( reportsDir, ID );
        FileWriter writer = null;
        try
        {
            reportsDir.mkdirs();

            writer = new FileWriter( report );
            output.output( doc, writer );
        }
        catch ( final IOException e )
        {
            throw new VManException( "Failed to generate %s report! Error: %s", e, ID, e.getMessage() );
        }
        finally
        {
            closeQuietly( writer );
        }
    }

    @Override
    public String getDescription()
    {
        return "Listing of parent POM references that were not listed in the BOM(s). Versions of parent references may be standardized if the parents are listed in the BOM.";
    }

}
