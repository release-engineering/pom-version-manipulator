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
import java.util.TreeMap;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.IOUtil;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.DependencyManagementKey;

@Component( role = Report.class, hint = MappedDependenciesReport.ID )
public class MappedDependenciesReport
    extends AbstractReport
{
    public static final String ID = "mapped-dependencies.txt";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public void generate( final File reportsDir, final VersionManagerSession sessionData )
        throws VManException
    {
        final File report = new File( reportsDir, ID );

        BufferedWriter writer = null;
        try
        {
            writer = new BufferedWriter( new FileWriter( report ) );

            final Map<File, Map<DependencyManagementKey, String>> byBom = sessionData.getMappedDependenciesByBom();
            for ( final Map.Entry<File, Map<DependencyManagementKey, String>> bomEntry : byBom.entrySet() )
            {
                final File bom = bomEntry.getKey();
                final Map<DependencyManagementKey, String> deps =
                    new TreeMap<DependencyManagementKey, String>( bomEntry.getValue() );

                writer.write( bom.getPath() );
                writer.write( " (" + deps.size() + " entries):" );

                writer.newLine();
                writer.write( "------------------------------------------------------" );
                writer.newLine();
                writer.newLine();

                for ( final Map.Entry<DependencyManagementKey, String> depEntry : deps.entrySet() )
                {
                    final DependencyManagementKey key = depEntry.getKey();
                    final String version = depEntry.getValue();

                    writer.write( key.toString() );
                    writer.write( " = " );
                    writer.write( version );
                    writer.newLine();
                }

                writer.newLine();
                writer.newLine();
                writer.newLine();
            }
        }
        catch ( final IOException e )
        {
            throw new VManException( "Failed to write to: %s. Reason: %s", e, report, e.getMessage() );
        }
        finally
        {
            IOUtil.close( writer );
        }
    }

    @Override
    public String getDescription()
    {
        return "Listing of available managed dependencies by BOM";
    }

}
