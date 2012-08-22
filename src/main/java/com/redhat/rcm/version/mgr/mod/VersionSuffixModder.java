/*
 * Copyright (c) 2010 Red Hat, Inc.
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

import org.apache.log4j.Logger;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.codehaus.plexus.component.annotations.Component;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectModder.class, hint = "version-suffix" )
public class VersionSuffixModder
    implements ProjectModder
{

    private static final Logger LOGGER = Logger.getLogger( VersionSuffixModder.class );

    public String getDescription()
    {
        return "Modify the POM version to include the supplied version suffix.";
    }

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        boolean changed = false;
        if ( session.getVersionSuffix() != null )
        {
            final String suffix = session.getVersionSuffix();
            final Model model = project.getModel();
            final Parent parent = project.getParent();

            if ( model.getVersion() != null && !model.getVersion().endsWith( suffix ) )
            {
                LOGGER.info( "Adding suffix: '" + suffix + "' to version: '" + model.getVersion() + "' for: "
                    + model.getId() );
                model.setVersion( model.getVersion() + suffix );
                changed = true;
            }

            if ( parent != null )
            {
                final ProjectKey tk = session.getToolchainKey();
                final VersionlessProjectKey vpk = new VersionlessProjectKey( parent );
                final String version = session.getArtifactVersion( vpk );

                // if the parent references a project in the current vman modification session...
                if ( session.inCurrentSession( parent ) )
                {
                    LOGGER.info( "Parent: '" + parent.getId() + "' is current session (for: " + model.getId() + ")" );
                    // and if the parent ref's version doesn't end with the suffix we're using here...
                    if ( !parent.getVersion().endsWith( suffix ) )
                    {
                        LOGGER.info( "Adding suffix: '" + suffix + "' to parent version: '" + parent.getVersion()
                            + "' for: " + model.getId() );

                        // adjust it.
                        parent.setVersion( parent.getVersion() + suffix );
                        changed = true;
                    }
                }
                // otherwise, if we're not using a toolchain POM or our parent references the
                // toolchain POM already, don't mess with the rest of this stuff.
                else if ( tk == null || new VersionlessProjectKey( tk ).equals( vpk ) )
                {
                    LOGGER.info( "Toolchain key: '" + tk + "' is null, or parent: '" + parent.getId()
                        + "' is already set to toolchain for: " + model.getId() + ". Nothing to do.." );
                    // NOP.
                }
                // if we do have a toolchain POM, and the parent ref for this project isn't listed in
                // a BOM (I know, that's weird)...
                else if ( version == null )
                {
                    // note it in the session that this parent POM hasn't been captured in our info.
                    session.addMissingParent( project );
                    if ( !session.isStrict() && !parent.getVersion().endsWith( suffix ) )
                    {
                        LOGGER.info( "Adding suffix: '" + suffix + "' to parent version: '" + parent.getVersion()
                            + "' for: " + model.getId() );

                        // if we're not operating in strict mode, and the parent isn't in the current
                        // VMan session, AND the parent ref version doesn't
                        // end with the suffix we're using, append it and assume that the parent POM
                        // will be VMan-ized and built using the same configuration.
                        parent.setVersion( parent.getVersion() + suffix );
                    }
                    else
                    {
                        LOGGER.info( "NOT adding suffix: '" + suffix + "' to parent version: '" + parent.getVersion()
                            + "' for: " + model.getId()
                            + "; either we're operating in strict mode, or the parent version is correct." );
                    }
                }
                // if we're using a different version of a parent listed in our BOMs
                else if ( !parent.getVersion().equals( version ) )
                {
                    LOGGER.info( "Adjusting parent version to: '" + version + "' (was: '" + parent.getVersion()
                        + "') for parent: '" + parent.getId() + "' in POM: " + model.getId() );

                    // adjust the parent version to match the BOM.
                    parent.setVersion( version );
                    changed = true;
                }
            }
        }

        try
        {
            project.updateCoord();
        }
        catch ( final ProjectToolsException e )
        {
            session.addError( e );
        }

        return changed;
    }

}
