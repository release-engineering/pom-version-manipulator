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
import org.apache.maven.model.Exclusion;
import org.codehaus.plexus.component.annotations.Component;
import org.jdom.Comment;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.Format.TextMode;
import org.jdom.output.XMLOutputter;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.VersionManagerSession;

@Component( role = Report.class, hint = MissingDependencyManagementReport.ID )
public class MissingDependencyManagementReport
    implements Report
{

    public static final String ID = "missing-dependencyManagement";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public void generate( final File reportsDir, final VersionManagerSession session )
        throws VManException
    {
        Map<VersionlessProjectKey, Set<Dependency>> missingDependencies = session.getMissingDependencies();
        Element deps = new Element( "dependencies" );

        for ( Map.Entry<VersionlessProjectKey, Set<Dependency>> depsEntry : missingDependencies.entrySet() )
        {
            if ( deps.getContentSize() > 0 )
            {
                deps.addContent( "\n\n" );
            }

            deps.addContent( new Comment( "START: " + depsEntry.getKey() ) );

            for ( Dependency dep : depsEntry.getValue() )
            {
                Element d = new Element( "dependency" );
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

                if ( dep.isOptional() )
                {
                    d.addContent( new Element( "optional" ).setText( Boolean.toString( true ) ) );
                }

                if ( dep.getExclusions() != null && !dep.getExclusions().isEmpty() )
                {
                    Element ex = new Element( "exclusions" );
                    d.addContent( ex );

                    for ( Exclusion exclusion : dep.getExclusions() )
                    {
                        ex.addContent( new Element( "groupId" ).setText( exclusion.getGroupId() ) );
                        ex.addContent( new Element( "artifactId" ).setText( exclusion.getArtifactId() ) );
                    }
                }
            }

            deps.addContent( new Comment( "END: " + depsEntry.getKey() ) );
        }

        Element dm = new Element( "dependencyManagement" );
        dm.setContent( deps );

        Document doc = new Document( dm );

        Format fmt = Format.getPrettyFormat();
        fmt.setIndent( "  " );
        fmt.setTextMode( TextMode.PRESERVE );

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
