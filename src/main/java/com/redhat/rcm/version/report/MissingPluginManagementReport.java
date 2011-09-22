package com.redhat.rcm.version.report;

import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.component.annotations.Component;
import org.jdom.Comment;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.Format.TextMode;
import org.jdom.output.XMLOutputter;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.VersionManagerSession;

@Component( role = Report.class, hint = MissingPluginManagementReport.ID )
public class MissingPluginManagementReport
    implements Report
{

    public static final String ID = "missing-pluginManagement";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public void generate( final File reportsDir, final VersionManagerSession session )
        throws VManException
    {
        Map<VersionlessProjectKey, Set<Plugin>> missingPlugins = session.getUnmanagedPluginRefs();
        Element plugins = new Element( "plugins" );

        for ( Map.Entry<VersionlessProjectKey, Set<Plugin>> pluginsEntry : missingPlugins.entrySet() )
        {
            if ( plugins.getContentSize() > 0 )
            {
                plugins.addContent( "\n\n" );
            }

            plugins.addContent( new Comment( "START: " + pluginsEntry.getKey() ) );

            for ( Plugin dep : pluginsEntry.getValue() )
            {
                Element d = new Element( "plugin" );
                plugins.addContent( d );

                d.addContent( new Element( "groupId" ).setText( dep.getGroupId() ) );
                d.addContent( new Element( "artifactId" ).setText( dep.getArtifactId() ) );
                d.addContent( new Element( "version" ).setText( dep.getVersion() ) );
            }

            plugins.addContent( new Comment( "END: " + pluginsEntry.getKey() ) );
        }

        Element build = new Element( "build" );
        build.addContent( new Element( "pluginManagement" ).setContent( plugins ) );

        Document doc = new Document( build );

        Format fmt = Format.getPrettyFormat();
        fmt.setIndent( "  " );
        fmt.setTextMode( TextMode.NORMALIZE );

        XMLOutputter output = new XMLOutputter();

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
