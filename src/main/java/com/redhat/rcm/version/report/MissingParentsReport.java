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
    implements Report
{

    public static final String ID = "missing-parents";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public void generate( final File reportsDir, final VersionManagerSession session )
        throws VManException
    {
        Set<Project> projectsWithMissingParent = session.getProjectsWithMissingParent();
        if ( projectsWithMissingParent.isEmpty() )
        {
            return;
        }

        Map<VersionlessProjectKey, Set<Project>> projectsByParent = new HashMap<VersionlessProjectKey, Set<Project>>();
        for ( Project project : projectsWithMissingParent )
        {
            VersionlessProjectKey parentKey = new VersionlessProjectKey( project.getParent() );
            Set<Project> projects = projectsByParent.get( parentKey );
            if ( projects == null )
            {
                projects = new HashSet<Project>();
                projectsByParent.put( parentKey, projects );
            }

            projects.add( project );
        }

        Element parents = new Element( "missing-parents" );
        for ( Map.Entry<VersionlessProjectKey, Set<Project>> parentEntry : projectsByParent.entrySet() )
        {
            if ( parents.getContentSize() > 0 )
            {
                parents.addContent( new Text( "\n\n" ) );
            }

            parents.addContent( new Comment( "START: Parent " + parentEntry.getKey() ) );
            boolean first = true;
            for ( Project project : parentEntry.getValue() )
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

                Parent parent = project.getParent();
                Element p = new Element( "parent" );
                parents.addContent( p );

                p.addContent( new Element( "groupId" ).setText( parent.getGroupId() ) );
                p.addContent( new Element( "artifactId" ).setText( parent.getArtifactId() ) );
                p.addContent( new Element( "version" ).setText( parent.getVersion() ) );
            }
        }

        Document doc = new Document( parents );

        Format fmt = Format.getPrettyFormat();
        fmt.setIndent( "  " );
        fmt.setTextMode( TextMode.PRESERVE );

        XMLOutputter output = new XMLOutputter( fmt );

        File report = new File( reportsDir, ID + ".xml" );
        FileWriter writer = null;
        try
        {
            reportsDir.mkdirs();

            writer = new FileWriter( report );
            output.output( doc, writer );
        }
        catch ( IOException e )
        {
            throw new VManException( "Failed to generate %s report! Error: %s", e, ID, e.getMessage() );
        }
        finally
        {
            closeQuietly( writer );
        }
    }

}
