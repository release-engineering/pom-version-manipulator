/*
 *  Copyright (C) 2012 John Casey.
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.redhat.rcm.version.testutil;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.io.IOUtils.copy;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Random;

public final class HttpTestService
{

    private static final int MAX_TRIES = 3;

    private final Map<String, URL> paths;

    private Server server;

    public HttpTestService( final Map<String, URL> paths )
    {
        this.paths = paths;
    }

    public String start()
        throws Exception
    {
        final Random rand = new Random();
        int tries = 0;

        String baseUrl = null;
        while ( tries < MAX_TRIES )
        {
            final int port = ( Math.abs( rand.nextInt() ) % 63000 ) + 1024;
            server = new Server( port );
            server.setHandler( new AbstractHandler()
            {
                @Override
                public void handle( final String target, final Request baseRequest, final HttpServletRequest request,
                                    final HttpServletResponse response )
                    throws IOException, ServletException
                {
                    System.out.println( "GET: " + target );
                    final URL res = paths.get( target );
                    if ( res != null )
                    {
                        System.out.println( " --> " + res.toExternalForm() );
                        InputStream stream = null;
                        try
                        {
                            stream = res.openStream();
                            copy( stream, response.getOutputStream() );
                        }
                        finally
                        {
                            closeQuietly( stream );
                        }

                        baseRequest.setHandled( true );
                    }
                    else
                    {
                        response.sendError( 404 );
                    }
                }
            } );

            try
            {
                server.start();

                baseUrl = "http://localhost:" + server.getConnectors()[0].getPort();
                System.out.println( "HTTP server started on port: " + port );
                break;
            }
            catch ( final Exception e )
            {
                e.printStackTrace();
            }

            tries++;
        }

        if ( tries > MAX_TRIES )
        {
            throw new IllegalStateException( "Cannot start HTTP server." );
        }

        return baseUrl;
    }

    public void stop()
    {
        try
        {
            if ( server != null )
            {
                server.stop();
            }
        }
        catch ( final Exception e )
        {
            e.printStackTrace();
            System.err.println( "\n\n\nERROR: Failed to shutdown HTTP service.\n\n\n" );
        }
    }

}
