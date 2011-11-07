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

package com.redhat.rcm.version.mgr.capture;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.mae.project.key.VersionlessProjectKey;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.WriterFactory;
import org.sonatype.aether.util.version.GenericVersionScheme;
import org.sonatype.aether.version.InvalidVersionSpecificationException;
import org.sonatype.aether.version.Version;
import org.sonatype.aether.version.VersionScheme;

import com.redhat.rcm.version.Cli;
import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.session.VersionManagerSession;

@Component( role = MissingInfoCapture.class )
public class MissingInfoCapture
{

    private static final String VERSION_DATE_PATTERN = "yyyyMMdd.HHmm";

    public void captureMissing( final VersionManagerSession session )
    {
        Map<VersionlessProjectKey, Set<Dependency>> missingDeps = session.getMissingDependencies();
        Map<VersionlessProjectKey, Set<Plugin>> missingPlugins = session.getUnmanagedPluginRefs();

        boolean procDeps = notEmpty( missingDeps );
        boolean procPlugs = notEmpty( missingPlugins );
        if ( procDeps || procPlugs )
        {
            SimpleDateFormat format = new SimpleDateFormat( VERSION_DATE_PATTERN );
            Model model = new Model();
            model.setModelVersion( "4.0.0" );

            model.setGroupId( Cli.class.getPackage().getName() );
            model.setArtifactId( "vman-missing-capture" );
            model.setVersion( format.format( new Date() ) );

            model.setPackaging( "pom" );

            boolean write = false;
            if ( procDeps )
            {
                write = processDependencies( missingDeps, model ) || write;
            }

            if ( procPlugs )
            {
                write = processPlugins( missingPlugins, model ) || write;
            }

            if ( write )
            {
                File capturePom = session.getCapturePom();
                Writer writer = null;
                try
                {
                    File dir = capturePom.getAbsoluteFile().getParentFile();
                    if ( dir != null && !dir.exists() )
                    {
                        dir.mkdirs();
                    }

                    writer = WriterFactory.newXmlWriter( capturePom );
                    new MavenXpp3Writer().write( writer, model );
                }
                catch ( IOException e )
                {
                    session.addError( new VManException( "Failed to write capture POM: %s. Reason: %s", e, capturePom,
                                                         e.getMessage() ) );
                }
                finally
                {
                    closeQuietly( writer );
                }
            }
        }
    }

    private boolean processPlugins( final Map<VersionlessProjectKey, Set<Plugin>> missingPlugins, final Model model )
    {
        Build build = new Build();
        PluginManagement pm = new PluginManagement();
        build.setPluginManagement( pm );

        for ( Map.Entry<VersionlessProjectKey, Set<Plugin>> entry : missingPlugins.entrySet() )
        {
            Map<String, Set<Plugin>> mks = new HashMap<String, Set<Plugin>>();
            for ( Plugin plugin : entry.getValue() )
            {
                String key = plugin.getKey();
                Set<Plugin> ds = mks.get( key );
                if ( ds == null )
                {
                    ds = new HashSet<Plugin>();
                    mks.put( key, ds );
                }

                ds.add( plugin );
            }

            for ( Set<Plugin> ds : mks.values() )
            {
                if ( ds == null || ds.isEmpty() )
                {
                    continue;
                }

                List<Plugin> pluginList = new ArrayList<Plugin>( ds );
                Collections.sort( pluginList, new PluginVersionComparator() );

                pm.addPlugin( pluginList.get( 0 ) );
            }
        }

        if ( pm.getPlugins() != null && !pm.getPlugins().isEmpty() )
        {
            Collections.sort( pm.getPlugins(), new PluginArtifactIdComparator() );

            model.setBuild( build );
            return true;
        }

        return false;
    }

    private boolean processDependencies( final Map<VersionlessProjectKey, Set<Dependency>> missingDeps,
                                         final Model model )
    {
        DependencyManagement dm = new DependencyManagement();
        for ( Map.Entry<VersionlessProjectKey, Set<Dependency>> entry : missingDeps.entrySet() )
        {
            Map<String, Set<Dependency>> mks = new HashMap<String, Set<Dependency>>();
            for ( Dependency dep : entry.getValue() )
            {
                String key = dep.getManagementKey();
                Set<Dependency> ds = mks.get( key );
                if ( ds == null )
                {
                    ds = new HashSet<Dependency>();
                    mks.put( key, ds );
                }

                ds.add( dep );
            }

            for ( Set<Dependency> ds : mks.values() )
            {
                if ( ds == null || ds.isEmpty() )
                {
                    continue;
                }

                List<Dependency> depList = new ArrayList<Dependency>( ds );
                Collections.sort( depList, new DependencyVersionComparator() );

                dm.addDependency( depList.get( 0 ) );
            }
        }

        if ( dm.getDependencies() != null && !dm.getDependencies().isEmpty() )
        {
            Collections.sort( dm.getDependencies(), new DependencyArtifactIdComparator() );

            model.setDependencyManagement( dm );
            return true;
        }

        return false;
    }

    private boolean notEmpty( final Map<?, ?> map )
    {
        return map != null && !map.isEmpty();
    }

    public static final class DependencyVersionComparator
        implements Comparator<Dependency>
    {

        private final VersionScheme versionScheme = new GenericVersionScheme();

        @Override
        public int compare( final Dependency o1, final Dependency o2 )
        {
            int result = 0;

            if ( isEmpty( o1.getVersion() ) && isNotEmpty( o2.getVersion() ) )
            {
                result = -1;
            }
            else if ( isNotEmpty( o1.getVersion() ) && isEmpty( o2.getVersion() ) )
            {
                result = 1;
            }
            else
            {
                Version v1 = null;
                try
                {
                    v1 = versionScheme.parseVersion( o1.getVersion() );
                }
                catch ( InvalidVersionSpecificationException e )
                {
                    result = -1;
                }

                if ( v1 != null )
                {
                    try
                    {
                        Version v2 = versionScheme.parseVersion( o2.getVersion() );

                        result = v1.compareTo( v2 );
                    }
                    catch ( InvalidVersionSpecificationException e )
                    {
                        result = 1;
                    }
                }

            }

            return result;
        }

    }

    public static final class PluginVersionComparator
        implements Comparator<Plugin>
    {

        private final VersionScheme versionScheme = new GenericVersionScheme();

        @Override
        public int compare( final Plugin o1, final Plugin o2 )
        {
            int result = 0;

            if ( isEmpty( o1.getVersion() ) && isNotEmpty( o2.getVersion() ) )
            {
                result = -1;
            }
            else if ( isNotEmpty( o1.getVersion() ) && isEmpty( o2.getVersion() ) )
            {
                result = 1;
            }
            else
            {
                Version v1 = null;
                try
                {
                    v1 = versionScheme.parseVersion( o1.getVersion() );
                }
                catch ( InvalidVersionSpecificationException e )
                {
                    result = -1;
                }

                if ( v1 != null )
                {
                    try
                    {
                        Version v2 = versionScheme.parseVersion( o2.getVersion() );

                        result = v1.compareTo( v2 );
                    }
                    catch ( InvalidVersionSpecificationException e )
                    {
                        result = 1;
                    }
                }

            }

            return result;
        }

    }

    public class DependencyArtifactIdComparator
        implements Comparator<Dependency>
    {
        @Override
        public int compare( final Dependency o1, final Dependency o2 )
        {
            return o1.getArtifactId().compareTo( o2.getArtifactId() );
        }
    }

    public class PluginArtifactIdComparator
        implements Comparator<Plugin>
    {
        @Override
        public int compare( final Plugin o1, final Plugin o2 )
        {
            return o1.getArtifactId().compareTo( o2.getArtifactId() );
        }
    }

}
