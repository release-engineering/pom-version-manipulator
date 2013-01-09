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

package com.redhat.rcm.version.mgr.mod;

import java.util.Comparator;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

public interface ProjectModder
{
    String STANDARD_MODS_ALIAS = "[standard]";

    String[] STANDARD_MODIFICATIONS = {"version-suffix", "toolchain-realignment", "bom-realignment", "repo-removal" };

    String[] MODIFICATION_ORDER = { "toolchain-realignment", "version-suffix", "version", "bom-realignment" };

    Comparator<String> KEY_COMPARATOR = new Comparator<String>()
    {
        @Override
        public int compare( final String one, final String two )
        {
            final Integer oneIdx = search( one );
            final Integer twoIdx = search( two );

            return oneIdx.compareTo( twoIdx );
        }

        private Integer search( final String value )
        {
            for ( int i = 0; i < MODIFICATION_ORDER.length; i++ )
            {
                if ( MODIFICATION_ORDER[i].equals( value ) )
                {
                    return i;
                }
            }

            return 9999;
        }
    };

    boolean inject( final Project project, final VersionManagerSession session );

    String getDescription();

}
