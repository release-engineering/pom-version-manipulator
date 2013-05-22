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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.IOUtil;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

@Component( role = Report.class, hint = RelocationsReport.ID )
public class RelocationsReport
    extends AbstractReport
{

    public static final String ID = "relocations.log";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public void generate( final File reportsDir, final VersionManagerSession sessionData )
        throws VManException
    {
        final File reportFile = new File( reportsDir, ID );

        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter( new FileWriter( reportFile ) );
            writer.printf( "ACTUAL RELOCATIONS (by POM):\n---------------------------------------------------------\n\n" );
            final Map<File, Map<ProjectKey, FullProjectKey>> relocationsByPom =
                sessionData.getRelocatedCoordinatesByFile();

            for ( final Map.Entry<File, Map<ProjectKey, FullProjectKey>> pomEntry : relocationsByPom.entrySet() )
            {
                final File pom = pomEntry.getKey();
                writer.printf( "%s\n---------------------------------------------------------\n", pom );
                for ( final Map.Entry<ProjectKey, FullProjectKey> relo : pomEntry.getValue()
                                                                                 .entrySet() )
                {
                    writer.printf( "\n    %s => %s", relo.getKey(), relo.getValue() );
                }
                writer.println();
                writer.println();
            }

            writer.printf( "\n\nALL AVAILABLE RELOCATIONS:\n---------------------------------------------------------\n" );
            final Map<File, Map<VersionlessProjectKey, FullProjectKey>> byFile = sessionData.getRelocations()
                                                                                            .getRelocationsByFile();

            for ( final Map.Entry<File, Map<VersionlessProjectKey, FullProjectKey>> fileEntry : byFile.entrySet() )
            {
                writer.printf( "%s\n---------------------------------------------------------\n", fileEntry.getKey() );
                for ( final Map.Entry<VersionlessProjectKey, FullProjectKey> coordEntry : fileEntry.getValue()
                                                                                                   .entrySet() )
                {
                    writer.printf( "\n    %s => %s", coordEntry.getKey(), coordEntry.getValue() );
                }
                writer.println();
                writer.println();
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

    @Override
    public String getDescription()
    {
        return "Report of available relocations and where they were actually applied.";
    }

}
