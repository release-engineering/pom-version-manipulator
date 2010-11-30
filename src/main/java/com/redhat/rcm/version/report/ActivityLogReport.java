/*
 *  Copyright (C) 2010 John Casey.
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

package com.redhat.rcm.version.report;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.IOUtil;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.VersionManagerSession;
import com.redhat.rcm.version.util.ActivityLog;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

@Component( role = Report.class, hint = ActivityLogReport.ID )
public class ActivityLogReport
    implements Report
{

    public static final String ID = "activity-log";

    public String getId()
    {
        return ID;
    }

    public void generate( final File reportsDir, final VersionManagerSession sessionData )
        throws VManException
    {
        final File reportFile = new File( reportsDir, "activity.log" );

        BufferedWriter writer = null;
        try
        {
            writer = new BufferedWriter( new FileWriter( reportFile ) );

            for ( final Map.Entry<File, ActivityLog> entry : sessionData.getLogs().entrySet() )
            {
                writer.write( entry.getKey().getPath() );
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
            throw new VManException( "Failed to write to: %s. Reason: %s", e, reportFile, e.getMessage() );
        }
        finally
        {
            IOUtil.close( writer );
        }
    }

}
