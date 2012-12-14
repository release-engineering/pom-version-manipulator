/*
 *  Copyright (C) 2012 John Casey.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.redhat.rcm.version.mgr.mod;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.commonjava.util.logging.Logger;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.maven.EffectiveModelBuilder;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

@Component( role = ProjectModder.class, hint = "extensions-removal" )
public class ExtensionsRemovalModder
    implements ProjectModder
{
    private final Logger logger = new Logger( getClass() );

    @Requirement
    private EffectiveModelBuilder modelBuilder;

    @Override
    public String getDescription()
    {
        return "Remove <extensions/> elements from the POM.";
    }

    /**
     * {@inheritDoc}
     *
     * @see com.redhat.rcm.version.mgr.mod.ProjectModder#inject(com.redhat.rcm.version.model.Project,
     *      com.redhat.rcm.version.mgr.session.VersionManagerSession)
     */
    @Override
    public boolean inject( final Project project, final VersionManagerSession session )
    {
        final Model model = project.getModel();

        boolean changed = false;

        if ( modelBuilder != null )
        {
            try
            {
                modelBuilder.getEffectiveModel( project, session );
            }
            catch ( final VManException e )
            {
                logger.error( "Failed to build effective model for: %s. Reason: %s", e, project.getKey(),
                              e.getMessage() );
                session.addError( e );
            }
        }

        if ( model.getBuild() != null && model.getBuild()
                                              .getExtensions() != null && !model.getBuild()
                                                                                .getExtensions()
                                                                                .isEmpty() )
        {
            final List<Extension> extensions = model.getBuild()
                                                    .getExtensions();
            final Set<VersionlessProjectKey> whitelist = session.getExtensionsWhitelist();

            if ( whitelist != null && !whitelist.isEmpty() )
            {
                final Iterator<Extension> i = extensions.iterator();
                while ( i.hasNext() )
                {
                    final Extension e = i.next();
                    final VersionlessProjectKey key = new VersionlessProjectKey( e.getGroupId(), e.getArtifactId() );

                    logger.info( "ExtensionsRemoval - checking " + key + " against whitelist " + whitelist );

                    if ( !whitelist.contains( key ) )
                    {
                        i.remove();
                        changed = true;
                    }
                    else
                    {
                        // This is expensive, so only do it on demand.
                        // NOTE: After this, the project's effective model will be set (by the effective model builder)
                        if ( project.getEffectiveModel() == null )
                        {
                            if ( modelBuilder != null )
                            {
                                try
                                {
                                    modelBuilder.getEffectiveModel( project, session );
                                }
                                catch ( final VManException error )
                                {
                                    logger.error( "Failed to build effective model for: %s. Reason: %s", error,
                                                  project.getKey(), error.getMessage() );
                                    session.addError( error );
                                }
                            }
                        }

                        e.setVersion( session.replacePropertyVersion( project, e.getGroupId(), e.getArtifactId() ) );
                        changed = true;
                    }
                }
            }
            else
            {
                model.getBuild()
                     .setExtensions( Collections.<Extension> emptyList() );
                changed = true;
            }
        }

        return changed;
    }

}
