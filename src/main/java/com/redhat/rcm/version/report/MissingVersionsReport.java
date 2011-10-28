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

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.IOUtil;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component( role = Report.class, hint = MissingVersionsReport.ID )
public class MissingVersionsReport
    implements Report
{
    public static final String ID = "missing-versions";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public void generate( final File reportsDir, final VersionManagerSession sessionData )
        throws VManException
    {
        final File reportFile = new File( reportsDir, "missing-versions.log" );

        BufferedWriter writer = null;
        try
        {
            writer = new BufferedWriter( new FileWriter( reportFile ) );

            final Map<VersionlessProjectKey, Set<File>> missing =
                new TreeMap<VersionlessProjectKey, Set<File>>( sessionData.getMissingVersions() );
            for ( final Map.Entry<VersionlessProjectKey, Set<File>> entry : missing.entrySet() )
            {
                writer.write( entry.getKey().toString() );
                writer.newLine();
            }

            if ( !missing.isEmpty() )
            {
                writer.newLine();
                writer.write( "Details:" );
                writer.newLine();
                writer.write( "--------" );
                writer.newLine();
                writer.newLine();
            }

            for ( final Map.Entry<VersionlessProjectKey, Set<File>> entry : missing.entrySet() )
            {
                writer.write( entry.getKey().toString() );
                writer.write( ":" );
                writer.newLine();
                writer.write( "-----------------------------------------------------------" );
                writer.newLine();
                writer.newLine();

                for ( final File pom : entry.getValue() )
                {
                    writer.write( "\t- " );
                    writer.write( pom.getPath() );
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
