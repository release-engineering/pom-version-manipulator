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
import static org.apache.commons.lang.StringUtils.isNotEmpty;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.codehaus.plexus.component.annotations.Component;
import org.jdom.Comment;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.Format.TextMode;
import org.jdom.output.XMLOutputter;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

@Component( role = Report.class, hint = MissingDependencyManagementReport.ID )
public class MissingDependencyManagementReport
    extends AbstractReport
{

    public static final String ID = "missing-dependencyManagement.xml";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public void generate( final File reportsDir, final VersionManagerSession session )
        throws VManException
    {
        final Map<VersionlessProjectKey, Set<Dependency>> missingDependencies = session.getMissingDependencies();
        if ( missingDependencies.isEmpty() )
        {
            return;
        }

        final Element deps = new Element( "dependencies" );

        for ( final Map.Entry<VersionlessProjectKey, Set<Dependency>> depsEntry : missingDependencies.entrySet() )
        {
            if ( deps.getContentSize() > 0 )
            {
                deps.addContent( "\n\n" );
            }

            deps.addContent( new Comment( "START: " + depsEntry.getKey() ) );

            for ( final Dependency dep : depsEntry.getValue() )
            {
                final Element d = new Element( "dependency" );
                deps.addContent( d );

                d.addContent( new Element( "groupId" ).setText( dep.getGroupId() ) );
                d.addContent( new Element( "artifactId" ).setText( dep.getArtifactId() ) );
                d.addContent( new Element( "version" ).setText( dep.getVersion() ) );

                if ( isNotEmpty( dep.getType() ) && !"jar".equals( dep.getType() ) )
                {
                    d.addContent( new Element( "type" ).setText( dep.getType() ) );
                }

                if ( isNotEmpty( dep.getClassifier() ) )
                {
                    d.addContent( new Element( "classifier" ).setText( dep.getClassifier() ) );
                }

                // if ( dep.isOptional() )
                // {
                // d.addContent( new Element( "optional" ).setText( Boolean.toString( true ) ) );
                // }
                //
                // if ( dep.getExclusions() != null && !dep.getExclusions().isEmpty() )
                // {
                // Element ex = new Element( "exclusions" );
                // d.addContent( ex );
                //
                // for ( Exclusion exclusion : dep.getExclusions() )
                // {
                // ex.addContent( new Element( "groupId" ).setText( exclusion.getGroupId() ) );
                // ex.addContent( new Element( "artifactId" ).setText( exclusion.getArtifactId() ) );
                // }
                // }
            }

            deps.addContent( new Comment( "END: " + depsEntry.getKey() ) );
        }

        final Element dm = new Element( "dependencyManagement" );
        dm.setContent( deps );

        final Document doc = new Document( dm );

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
        return "pom.xml-compatible <dependencyManagement> section containing dependencies that were missing from BOM(s)";
    }

}
