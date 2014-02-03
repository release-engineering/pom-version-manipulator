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

import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.key.ProjectKey;
import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.DependencyManagementKey;
import com.redhat.rcm.version.model.Project;

public abstract class AbstractVersionModder
    implements ProjectModder
{
    protected final Logger logger = LoggerFactory.getLogger( getClass() );

    protected abstract String getActionDescription();

    protected abstract boolean initialiseModder( final Project project, final VersionManagerSession session );

    protected abstract boolean shouldModifyVersion( String version );

    protected abstract String replaceVersion( String model );

    /**
     * It is possible for a pom file to be a template. Don't alter the version in that case.
     * @param version
     * @return boolean
     */
    protected boolean isTemplateVersion( final String version )
    {
        if ( version.startsWith( "${" ) && version.endsWith( "}" ) )
        {
            return true;
        }
        return false;
    }

    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        boolean changed = false;

        if ( initialiseModder( project, session ) )
        {
            final Model model = project.getModel();
            final Parent parent = project.getParent();

            if ( model.getVersion() != null && shouldModifyVersion( model.getVersion() ) )
            {
                logger.info( getActionDescription() + " in: " + model.getVersion() + "' for: " + model.getId() );
                model.setVersion( replaceVersion( model.getVersion() ) );
                changed = true;
            }

            if ( parent != null )
            {
                final ProjectKey tk = session.getToolchainKey();
                final VersionlessProjectKey vpk = new VersionlessProjectKey( parent );

                Dependency managed =
                    session.getManagedDependency( new DependencyManagementKey( parent.getGroupId(), parent.getArtifactId(), "pom", null ) );

                if ( managed == null )
                {
                    // if we don't find the one with the specific "pom" type, look for the generic one
                    // if we find that, we can list the specific one as a missing parent/dep and use the info from
                    // the generic one for the version, etc.
                    managed = session.getManagedDependency( new DependencyManagementKey( parent.getGroupId(), parent.getArtifactId() ) );
                    if ( managed != null )
                    {
                        // TODO: Are BOTH of the following actions (missing parent, missing dep) REALLY appropriate??
                        session.addMissingParent( project );

                        //                        final Dependency d = new Dependency();
                        //                        d.setGroupId( parent.getGroupId() );
                        //                        d.setArtifactId( parent.getArtifactId() );
                        //                        d.setVersion( managed.getVersion() );
                        //                        d.setType( "pom" );
                        //
                        //                        session.addMissingDependency( project, d );
                    }
                }

                final String version = managed == null ? null : managed.getVersion();

                // if the parent references a project in the current vman modification session...
                if ( session.inCurrentSession( parent ) )
                {
                    logger.info( "Parent: '" + parent.getId() + "' is current session (for: " + model.getId() + ")" );
                    // and if the parent ref's version doesn't end with the suffix we're using here...
                    if ( shouldModifyVersion( parent.getVersion() ) )
                    {
                        logger.info( getActionDescription() + " in: " + model.getVersion() + "' for: " + model.getId() );
                        parent.setVersion( replaceVersion( parent.getVersion() ) );
                        changed = true;
                    }
                }
                // otherwise, if we're not using a toolchain POM or our parent references the
                // toolchain POM already, don't mess with the rest of this stuff.
                else if ( tk == null || new VersionlessProjectKey( tk ).equals( vpk ) )
                {
                    logger.info( "Toolchain key: '" + tk + "' is null, or parent: '" + parent.getId() + "' is already set to toolchain for: "
                        + model.getId() + ". Nothing to do.." );
                    // NOP.
                }
                else if ( session.getRelocation( vpk ) != null )
                {
                    logger.info( "Original parent " + vpk + " has been relocated to " + tk + " ; nothing to do." );
                    // NOP.
                }
                // if we do have a toolchain POM, and the parent ref for this project isn't listed in
                // a BOM (I know, that's weird)...
                else if ( version == null )
                {
                    // note it in the session that this parent POM hasn't been captured in our info.
                    session.addMissingParent( project );
                    if ( !session.isStrict() && shouldModifyVersion( parent.getVersion() ) )
                    {
                        // if we're not operating in strict mode, and the parent isn't in the current
                        // VMan session, AND the parent ref version doesn't
                        // end with the suffix we're using, append it and assume that the parent POM
                        // will be VMan-ized and built using the same configuration.
                        parent.setVersion( replaceVersion( parent.getVersion() ) );
                        changed = true;
                    }
                    else
                    {
                        logger.info( "NOT replacing snapshot for parent version: '" + parent.getVersion() + "' for: " + model.getId()
                            + "; either we're operating in strict mode, or the parent version is correct." );
                    }
                }
                // if we're using a different version of a parent listed in our BOMs
                else if ( !parent.getVersion()
                                 .equals( version ) )
                {
                    logger.info( "Adjusting parent version to: '" + version + "' (was: '" + parent.getVersion() + "') for parent: '" + parent.getId()
                        + "' in POM: " + model.getId() );

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
