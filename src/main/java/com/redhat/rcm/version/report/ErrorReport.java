/*
 * Copyright (c) 2011 Red Hat, Inc.
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import javax.inject.Named;
import javax.inject.Singleton;

import org.codehaus.plexus.util.IOUtil;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

@Singleton
@Named( "errors.log" )
public class ErrorReport
    implements Report
{

    @Override
    public void generate( final File reportsDir, final VersionManagerSession sessionData )
        throws VManException
    {
        BufferedWriter writer = null;
        try
        {
            writer = sessionData.openReportFile( this );

            final PrintWriter pWriter = new PrintWriter( writer );

            int count = 0;
            for ( final Throwable error : sessionData.getErrors() )
            {
                pWriter.printf( "%d:  ", count );
                error.printStackTrace( pWriter );
                pWriter.println();
                pWriter.println();
                count++;

                writer.newLine();
                writer.newLine();
            }
        }
        catch ( final IOException e )
        {
            throw new VManException( "Failed to write error report. Reason: %s", e, e.getMessage() );
        }
        finally
        {
            IOUtil.close( writer );
        }
    }

}
