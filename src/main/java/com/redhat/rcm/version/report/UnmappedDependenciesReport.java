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
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.IOUtil;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

@Component( role = Report.class, hint = UnmappedDependenciesReport.ID )
public class UnmappedDependenciesReport
    extends AbstractReport
{
    public static final String ID = "unmapped-dependencies.md";

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

        BufferedWriter writer = null;
        try
        {
            writer = new BufferedWriter( new FileWriter( reportFile ) );

            // NOTE: Using Markdown format...
            writer.write( "# Unmapped Dependencies\n\n\n" );

            final Map<FullProjectKey, Set<File>> missing =
                new TreeMap<FullProjectKey, Set<File>>( sessionData.getMissingVersions() );
            for ( final Map.Entry<FullProjectKey, Set<File>> entry : missing.entrySet() )
            {
                writer.write( "  - " );
                writer.write( entry.getKey()
                                   .toString() );
                writer.newLine();
            }

            if ( !missing.isEmpty() )
            {
                writer.newLine();
                writer.write( "## Details:");
                writer.newLine();
                writer.newLine();
            }

            for ( final Map.Entry<FullProjectKey, Set<File>> entry : missing.entrySet() )
            {
                writer.write( "### " );
                writer.write( new VersionlessProjectKey (entry.getKey()).toString() );
                writer.newLine();
                writer.newLine();

                for ( final File pom : entry.getValue() )
                {
                    writer.write( "  - " );
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

    @Override
    public String getDescription()
    {
        return "Listing of versions referenced by projects for dependencies that were missing from the BOM(s)";
    }

}
