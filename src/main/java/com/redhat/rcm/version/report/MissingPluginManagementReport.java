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
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Plugin;
import org.jdom.Comment;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.Format.TextMode;
import org.jdom.output.XMLOutputter;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

@Singleton
@Named( "missing-pluginManagement.xml" )
public class MissingPluginManagementReport
    implements Report
{

    @Override
    public void generate( final File reportsDir, final VersionManagerSession session )
        throws VManException
    {
        final Map<VersionlessProjectKey, Set<Plugin>> missingPlugins = session.getUnmanagedPluginRefs();
        if ( missingPlugins.isEmpty() )
        {
            return;
        }
        final Element plugins = new Element( "plugins" );

        for ( final Map.Entry<VersionlessProjectKey, Set<Plugin>> pluginsEntry : missingPlugins.entrySet() )
        {
            if ( plugins.getContentSize() > 0 )
            {
                plugins.addContent( "\n\n" );
            }

            plugins.addContent( new Comment( "START: " + pluginsEntry.getKey() ) );

            for ( final Plugin dep : pluginsEntry.getValue() )
            {
                final Element d = new Element( "plugin" );
                plugins.addContent( d );

                d.addContent( new Element( "groupId" ).setText( dep.getGroupId() ) );
                d.addContent( new Element( "artifactId" ).setText( dep.getArtifactId() ) );
                d.addContent( new Element( "version" ).setText( dep.getVersion() ) );
            }

            plugins.addContent( new Comment( "END: " + pluginsEntry.getKey() ) );
        }

        final Element build = new Element( "build" );
        build.addContent( new Element( "pluginManagement" ).setContent( plugins ) );

        final Document doc = new Document( build );

        final Format fmt = Format.getPrettyFormat();
        fmt.setIndent( "  " );
        fmt.setTextMode( TextMode.PRESERVE );

        final XMLOutputter output = new XMLOutputter( fmt );

        Writer writer = null;
        try
        {
            reportsDir.mkdirs();

            writer = session.openReportFile( this );
            output.output( doc, writer );
        }
        catch ( final IOException e )
        {
            throw new VManException( "Failed to generate missing-pluginManagement report! Error: %s", e, e.getMessage() );
        }
        finally
        {
            closeQuietly( writer );
        }
    }

}
