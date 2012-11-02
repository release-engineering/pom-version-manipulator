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

import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;


@Component( role = ProjectModder.class, hint = "version" )
public class VersionModder extends AbstractVersionModder
{
    private String []versionModifier;

    @Override
    public String getDescription()
    {
        return "Modify the POM version to change snapshot version.";
    }

    @Override
    protected String getActionDescription ()
    {
        return "Replacing " + versionModifier[1] + " with " + versionModifier[0];
    }

    @Override
    protected boolean verifyVersion (String version)
    {
        return (version.indexOf(versionModifier[0]) != -1) && !isTemplateVersion(version);
    }

    @Override
    protected String replaceVersion (String version)
    {
        return (version.replace(versionModifier[0], versionModifier[1]));
    }

    @Override
    protected boolean initialiseModder (final Project project, final VersionManagerSession session)
    {
        boolean result = (session.getVersionModifier() != null);

        if (result)
        {
            versionModifier = session.getVersionModifier().split( ":" );

            if (versionModifier.length != 2)
            {
                logger.error("Invalid version modifier size - should be two");
                session.addError(new VManException ("Invalid version modifier size. Should be 'pattern:replacement'."));
            }
        }
        return result;
    }
}
