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

import java.io.File;
import java.io.IOException;
import java.io.Writer;

import org.apache.log4j.Logger;
import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.jdom.MavenJDOMWriter;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.WriterFactory;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.Format.TextMode;
import org.jdom.output.XMLOutputter;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

public final class PomUtils
{

    private static final Logger LOGGER = Logger.getLogger( PomUtils.class );

    private PomUtils()
    {
    }

    public static File writeModifiedPom( final Model model, final File pom, final ProjectKey coord,
                                         final String version, final File basedir, final VersionManagerSession session,
                                         final boolean relocatePom )
    {
        File out = relocatePom ? generateRelocatedPomFile( coord, version, basedir ) : pom;

        Writer writer = null;
        try
        {
            final SAXBuilder builder = new SAXBuilder();
            final Document doc = builder.build( pom );

            String encoding = model.getModelEncoding();
            if ( encoding == null )
            {
                encoding = "UTF-8";
            }

            final Format format =
                Format.getRawFormat()
                      .setEncoding( encoding )
                      .setTextMode( TextMode.PRESERVE )
                      .setLineSeparator( System.getProperty( "line.separator" ) );

            LOGGER.info( "Writing modified POM:\n\n" + new XMLOutputter( format ).outputString( doc ) );

            session.getLog( pom ).add( "Writing modified POM: %s", out );
            writer = WriterFactory.newWriter( out, encoding );

            new MavenJDOMWriter().write( model, doc, writer, format );

            if ( relocatePom && !out.equals( pom ) )
            {
                session.getLog( pom ).add( "Deleting original POM: %s\nPurging unused directories...", pom );
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

    public static File generateRelocatedPomFile( final ProjectKey coord, final String version, final File basedir )
    {
        final StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append( coord.getGroupId().replace( '.', separatorChar ) )
                   .append( separatorChar )
                   .append( coord.getArtifactId() )
                   .append( separatorChar )
                   .append( version )
                   .append( separatorChar )
                   .append( coord.getArtifactId() )
                   .append( '-' )
                   .append( version )
                   .append( ".pom" );

        File out = new File( basedir, pathBuilder.toString() );
        final File outDir = out.getParentFile();
        outDir.mkdirs();

        return out;
    }

}
