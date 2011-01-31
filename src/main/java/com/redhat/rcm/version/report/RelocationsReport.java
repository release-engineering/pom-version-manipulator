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

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.IOUtil;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.VersionManagerSession;
import com.redhat.rcm.version.model.VersionlessProjectKey;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

@Component( role = Report.class, hint = RelocationsReport.ID )
public class RelocationsReport
    implements Report
{

    public static final String ID = "relocations-log";

    public String getId()
    {
        return ID;
    }

    public void generate( final File reportsDir, final VersionManagerSession sessionData )
        throws VManException
    {
        final File reportFile = new File( reportsDir, "relocations.log" );

        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter( new FileWriter( reportFile ) );
            final Map<File, Map<VersionlessProjectKey, VersionlessProjectKey>> byFile = sessionData.getRelocations().getRelocationsByFile();

            int fileCounter = 0;
            for ( final Map.Entry<File, Map<VersionlessProjectKey, VersionlessProjectKey>> fileEntry : byFile.entrySet() )
            {
                writer.printf( "%d: %s\n---------------------------------------------------------\n", fileCounter,
                               fileEntry.getKey() );
                for ( final Map.Entry<VersionlessProjectKey, VersionlessProjectKey> coordEntry : fileEntry.getValue().entrySet() )
                {
                    writer.printf( "\n    %s = %s", coordEntry.getKey(), coordEntry.getValue() );
                }
                writer.println();
                writer.println();

                fileCounter++;
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
