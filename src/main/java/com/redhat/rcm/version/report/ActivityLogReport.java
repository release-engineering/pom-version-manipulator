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
import java.util.Map;

import javax.inject.Named;
import javax.inject.Singleton;

import org.codehaus.plexus.util.IOUtil;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.util.ActivityLog;

@Singleton
@Named( "activity.log" )
public class ActivityLogReport
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

            for ( final Map.Entry<File, ActivityLog> entry : sessionData.getLogs()
                                                                        .entrySet() )
            {
                writer.write( entry.getKey()
                                   .getPath() );
                writer.write( ":" );
                writer.newLine();
                writer.write( "-----------------------------------------------------------" );
                writer.newLine();
                writer.newLine();

                for ( final String line : entry.getValue() )
                {
                    writer.write( line );
                    writer.newLine();
                }
                writer.newLine();
                writer.newLine();
            }
        }
        catch ( final IOException e )
        {
            throw new VManException( "Failed to write activity log. Reason: %s", e, e.getMessage() );
        }
        finally
        {
            IOUtil.close( writer );
        }
    }

}
