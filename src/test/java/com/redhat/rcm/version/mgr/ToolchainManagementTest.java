/*
 *  Copyright (C) 2011 John Casey.
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

package com.redhat.rcm.version.mgr;

import static org.apache.commons.io.IOUtils.*;
import static org.apache.commons.io.FileUtils.*;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Test;

import com.redhat.rcm.version.VManException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ToolchainManagementTest
    extends AbstractVersionManagerTest
{

    @Test
    public void adjustNonManagedPluginForInheritedToolchain()
        throws Throwable
    {
        Model changed = adjustSingle( "Adjust single non-managed plugin in POM inherited from toolchain", "toolchain-child-nonManaged-1.0.pom" );
        Build build = changed.getBuild();
        assertThat( build, notNullValue() );
        
        List<Plugin> plugins = build.getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );
        
        Plugin plugin = plugins.get( 0 );
        assertThat( plugin.getArtifactId(), equalTo( "maven-compiler-plugin" ) );
        assertThat( plugin.getVersion(), nullValue() );
    }
    
    @Test
    public void adjustManagedPluginForInheritedToolchain()
        throws Throwable
    {
        Model changed = adjustSingle( "Adjust managed plugin in POM inherited from toolchain", "toolchain-child-managed-1.0.pom" );
        
        Build build = changed.getBuild();
        assertThat( build, notNullValue() );
        
        PluginManagement pm = build.getPluginManagement();
        assertThat( pm, notNullValue() );
        
        List<Plugin> plugins = pm.getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );
        
        Plugin plugin = plugins.get( 0 );
        assertThat( plugin.getArtifactId(), equalTo( "maven-compiler-plugin" ) );
        assertThat( plugin.getVersion(), nullValue() );
    }

    @Test
    public void adjustNonManagedPluginForNonInheritedToolchain()
        throws Throwable
    {
        Model changed = adjustSingle( "Adjust non-managed plugin in POM NOT inheriting from toolchain", "toolchain-nonChild-nonManaged-1.0.pom" );
        
        Build build = changed.getBuild();
        assertThat( build, notNullValue() );
        
        List<Plugin> plugins = build.getPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );
        
        Plugin plugin = plugins.get( 0 );
        assertThat( plugin.getArtifactId(), equalTo( "maven-compiler-plugin" ) );
        assertThat( plugin.getVersion(), nullValue() );

        PluginManagement pm = build.getPluginManagement();
        assertThat( pm, notNullValue() );
        
        Map<String, Plugin> pluginMap = pm.getPluginsAsMap();
        assertThat( pluginMap, notNullValue() );
        
        plugin = pluginMap.get( "org.apache.maven.plugins:maven-compiler-plugin" );
        assertThat( plugin.getVersion(), equalTo( "2.0" ) );
    }

    @Test
    public void removeEmptyNonManagedPluginForInheritedToolchain()
        throws Throwable
    {
        Model changed = adjustSingle( "Adjust single non-managed plugin in POM inherited from toolchain", "toolchain-child-nonManaged-emptyPlugin-1.0.pom" );
        Build build = changed.getBuild();
        assertThat( build, notNullValue() );
        
        List<Plugin> plugins = build.getPlugins();
        assertThat( plugins == null || plugins.isEmpty(), is( true ) );
    }
    
    @Test
    public void removeEmptyManagedPluginForInheritedToolchain()
        throws Throwable
    {
        Model changed = adjustSingle( "Adjust managed plugin in POM inherited from toolchain", "toolchain-child-managed-emptyPlugin-1.0.pom" );
        
        Build build = changed.getBuild();
        assertThat( build, notNullValue() );
        
        PluginManagement pm = build.getPluginManagement();
        assertThat( pm, notNullValue() );
        
        List<Plugin> plugins = pm.getPlugins();
        assertThat( plugins == null || plugins.isEmpty(), is( true ) );
    }

    @Test
    public void removeEmptyNonManagedPluginForNonInheritedToolchain()
        throws Throwable
    {
        Model changed = adjustSingle( "Adjust non-managed plugin in POM NOT inheriting from toolchain", "toolchain-nonChild-nonManaged-emptyPlugin-1.0.pom" );
        
        Build build = changed.getBuild();
        assertThat( build, notNullValue() );
        
        List<Plugin> plugins = build.getPlugins();
        assertThat( plugins == null || plugins.isEmpty(), is( true ) );
        
        PluginManagement pm = build.getPluginManagement();
        assertThat( pm, notNullValue() );
        
        Map<String, Plugin> pluginMap = pm.getPluginsAsMap();
        assertThat( pluginMap, notNullValue() );
        
        Plugin plugin = pluginMap.get( "org.apache.maven.plugins:maven-compiler-plugin" );
        assertThat( plugin.getVersion(), equalTo( "2.0" ) );
    }

    private Model adjustSingle( String description, String pomPath ) throws Throwable
    {
        System.setProperty( "debug", Boolean.toString( true ) );
        System.out.println( "TESTING: " + description );

        final File srcPom = getResourceFile( pomPath );
        final String toolchain = getResourceFile( "toolchain-1.0.pom" ).getAbsolutePath();

        final File pom = new File( repo, srcPom.getName() );
        copyFile( srcPom, pom );

        final VersionManagerSession session = new VersionManagerSession( workspace, reports, false, true, false );

        final Set<File> modified = vman.modifyVersions( pom, null, toolchain, session );
        assertNoErrors( session );
        
        Set<Model> changedModels = loadModels( modified );
        assertThat( changedModels.size(), equalTo( 1 ) );
        
        return changedModels.iterator().next();
    }

    private Set<Model> loadModels( Set<File> poms )
        throws VManException
    {
        Set<Model> models = new LinkedHashSet<Model>( poms.size() );
        for ( File pom : poms )
        {
            FileInputStream stream = null;
            try
            {
                stream = new FileInputStream( pom );
                models.add( new MavenXpp3Reader().read( stream ) );
            }
            catch ( IOException e )
            {
                throw new VManException( "Failed to read modified POM: %s. Reason: %s", e, pom, e.getMessage() );
            }
            catch ( XmlPullParserException e )
            {
                throw new VManException( "Failed to parse modified POM: %s. Reason: %s", e, pom, e.getMessage() );
            }
            finally
            {
                closeQuietly( stream );
            }
        }
        
        return models;
    }

}
