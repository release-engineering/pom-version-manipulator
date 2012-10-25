/*
 * Copyright (c) 2012 Red Hat, Inc.
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

import javax.inject.Named;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Named( "modder/version-suffix" )
public class VersionSuffixModder
    extends AbstractVersionModder
{
    private String suffix;

    @Override
    public String getDescription()
    {
        return "Modify the POM version to include the supplied version suffix.";
    }

    @Override
    protected String getActionDescription()
    {
        return "Adding suffix " + suffix;
    }

    @Override
    protected boolean verifyVersion( final String version )
    {
        return !version.endsWith( suffix ) && !isTemplateVersion( version );
    }

    @Override
    protected String replaceVersion( final String version )
    {
        return ( version + suffix );
    }

    @Override
    protected boolean initialiseModder( final Project project, final VersionManagerSession session )
    {
        final boolean result = ( session.getVersionSuffix() != null );

        if ( result )
        {
            suffix = session.getVersionSuffix();
        }
        return result;
    }
}
