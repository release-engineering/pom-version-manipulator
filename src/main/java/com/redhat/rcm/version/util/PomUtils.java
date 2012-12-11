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

package com.redhat.rcm.version.util;

import static java.io.File.separatorChar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.jdom.MavenJDOMWriter;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.commonjava.util.logging.Logger;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.Format.TextMode;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

public final class PomUtils
{

    private static final Logger LOGGER = new Logger( PomUtils.class );

    private PomUtils()
    {
    }

    public static Model cloneModel( final Model src )
        throws VManException
    {
        try
        {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new MavenXpp3Writer().write( baos, src );
            return new MavenXpp3Reader().read( new ByteArrayInputStream( baos.toByteArray() ) );
        }
        catch ( final IOException e )
        {
            throw new VManException( "Failed to clone model %s via serialization/deserialization. Reason: %s", e, src,
                                     e.getMessage() );
        }
        catch ( final XmlPullParserException e )
        {
            throw new VManException( "Failed to clone model %s via serialization/deserialization. Reason: %s", e, src,
                                     e.getMessage() );
        }
    }

    public static File writeModifiedPom( final Model model, final File pom, final ProjectKey coord,
                                         final String version, final File basedir, final VersionManagerSession session,
                                         final boolean relocatePom )
    {
        final File out = relocatePom ? generateRelocatedPomFile( coord, version, basedir ) : pom;

        Writer writer = null;
        try
        {
            final SAXBuilder builder = new SAXBuilder();
            Document doc = builder.build( pom );

            String encoding = model.getModelEncoding();
            if ( encoding == null )
            {
                encoding = "UTF-8";
            }

            final Format format = Format.getRawFormat()
                                        .setEncoding( "UTF-8" )
                                        .setTextMode( TextMode.PRESERVE )
                                        .setLineSeparator( System.getProperty( "line.separator" ) )
                                        .setOmitDeclaration( false )
                                        .setOmitEncoding( false );

            LOGGER.info( "Writing modified POM:\n\n" + new XMLOutputter( format ).outputString( doc ) );

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer = WriterFactory.newWriter( baos, encoding );

            new MavenJDOMWriter().write( model, doc, writer, format );

            doc = builder.build( new ByteArrayInputStream( baos.toByteArray() ) );

            normalizeNamespace( doc );

            session.getLog( pom )
                   .add( "Writing modified POM: %s", out );
            writer = WriterFactory.newWriter( out, encoding );
            new XMLOutputter( format ).output( doc, writer );

            if ( relocatePom && !out.equals( pom ) )
            {
                session.getLog( pom )
                       .add( "Deleting original POM: %s\nPurging unused directories...", pom );
                pom.delete();
                File dir = pom.getParentFile();
                while ( dir != null && !basedir.equals( dir ) )
                {
                    final String[] listing = dir.list();
                    if ( listing == null || listing.length < 1 )
                    {
                        dir.delete();
                        dir = dir.getParentFile();
                    }
                    else
                    {
                        break;
                    }
                }
            }
        }
        catch ( final IOException e )
        {
            session.addError( new VManException( "Failed to write modified POM: %s to: %s\n\tReason: %s", e, pom, out,
                                                 e.getMessage() ) );
        }
        catch ( final JDOMException e )
        {
            session.addError( new VManException( "Failed to read original POM for rewrite: %s\n\tReason: %s", e, pom,
                                                 e.getMessage() ) );
        }
        finally
        {
            IOUtil.close( writer );
        }

        return out;
    }

    /**
     * @param project
     * @param ns
     */
    private static void normalizeNamespace( final Document doc )
    {
        final Namespace ns = Namespace.getNamespace( "http://maven.apache.org/POM/4.0.0" );
        final Element project = doc.getRootElement();
        if ( !ns.equals( project.getNamespace() ) )
        {
            project.setNamespace( ns );
        }

        Namespace xsi = project.getNamespace( "xsi" );
        if ( xsi == null )
        {
            xsi = Namespace.getNamespace( "xsi", "http://www.w3.org/2001/XMLSchema-instance" );
            project.addNamespaceDeclaration( xsi );
        }

        Attribute schemaLocation = project.getAttribute( "schemaLocation", ns );
        if ( schemaLocation == null )
        {
            schemaLocation =
                new Attribute( "schemaLocation",
                               "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd", xsi );

            project.setAttribute( schemaLocation );
        }

        try
        {
            @SuppressWarnings( "unchecked" )
            final List<Element> allNodes = XPath.selectNodes( project, "//*" );
            for ( final Element node : allNodes )
            {
                if ( node.getNamespace() == null || node.getNamespace() == Namespace.NO_NAMESPACE
                    || node.getNamespace()
                           .getURI()
                           .trim()
                           .length() < 1 )
                {
                    node.setNamespace( ns );
                }
            }
        }
        catch ( final JDOMException e )
        {
            LOGGER.error( "Failed to select all nodes in the document.", e );
        }
    }

    public static File generateRelocatedPomFile( final ProjectKey coord, final String version, final File basedir )
    {
        final StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append( coord.getGroupId()
                                 .replace( '.', separatorChar ) )
                   .append( separatorChar )
                   .append( coord.getArtifactId() )
                   .append( separatorChar )
                   .append( version )
                   .append( separatorChar )
                   .append( coord.getArtifactId() )
                   .append( '-' )
                   .append( version )
                   .append( ".pom" );

        final File out = new File( basedir, pathBuilder.toString() );
        final File outDir = out.getParentFile();
        outDir.mkdirs();

        return out;
    }

}
