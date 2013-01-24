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

package com.redhat.rcm.version.util;

import static com.redhat.rcm.version.testutil.TestProjectFixture.getResourceFile;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.fixture.LoggingFixture;

public class InputUtilsTest
{

    @BeforeClass
    public static void enableLogging()
    {
        LoggingFixture.setupLogging();
    }

    @Test
    public void parsePropertiesFile_HandlingOfCoordinates_Comments_AndCommaSeparatedProps()
        throws VManException
    {
        final File relocations = getResourceFile( "relocations.properties" );
        final Map<String, String> relos = InputUtils.readProperties( relocations );

        assertThat( relos.get( "org.foo:bar:1.0" ), equalTo( "com.foo:bar:1.0.0" ) );
        assertThat( relos.get( "one" ), equalTo( "two" ) );
        assertThat( relos.get( "three" ), equalTo( "four" ) );
    }

    @Test
    public void checkParseProperties_HandlingOfSpaceNewlineComma()
    {
        final String inGA1 = "org.foo:foo";
        final String outGA1 = "org.bar:foo:1.0";

        final String inGA2 = "my.proj:core";
        final String outGA2 = "org.proj:core:1.0";

        final Map<String, String> props =
            InputUtils.parseProperties( inGA1 + "=" + outGA1 + " \n," + inGA2 + "=" + outGA2 );

        assertThat( props.get( "org.foo:foo" ), equalTo( "org.bar:foo:1.0" ) );
        assertThat( props.get( "my.proj:core" ), equalTo( "org.proj:core:1.0" ) );
    }

    @Test
    public void checkParseProperties_HandlingOfSpaceCommaNewlineTab()
    {
        final String inGA1 = "org.foo:foo";
        final String outGA1 = "org.bar:foo:1.0";

        final String inGA2 = "my.proj:core";
        final String outGA2 = "org.proj:core:1.0";

        final Map<String, String> props =
            InputUtils.parseProperties( inGA1 + "=" + outGA1 + " \n," + inGA2 + "=" + outGA2 );

        assertThat( props.get( "org.foo:foo" ), equalTo( "org.bar:foo:1.0" ) );
        assertThat( props.get( "my.proj:core" ), equalTo( "org.proj:core:1.0" ) );
    }

    @Test
    public void checkParseProperties_HandlingOfGitwebUrl()
    {
        final Map<String, String> props =
            InputUtils.parseProperties( "bootstrap=http://gitweb.company.com/?p=proj/my-project.git;a=blob_plain;f=vman.properties;hb=HEAD" );

        assertThat( props.get( "bootstrap" ),
                    equalTo( "http://gitweb.company.com/?p=proj/my-project.git;a=blob_plain;f=vman.properties;hb=HEAD" ) );
    }

    @Test
    public void checkParseProperties_InvalidLineDoesNotStopOtherAdditions()
    {
        final String inGA1 = "org.foo:foo";
        final String outGA1 = "org.bar:foo:1.0";

        final String inGA2 = "my.proj:core";
        final String outGA2 = "org.proj:core:1.0";

        final String inGA3 = "com.foo:project:1";
        final String outGA3 = "com.myco.foo:project1";

        final Map<String, String> p =
            InputUtils.parseProperties( inGA1 + "=" + outGA1 + " ,\n\t" + inGA2 + "=" + outGA2 + ",\n\t" + inGA3 + "="
                + outGA3 );

        assertThat( p.get( "org.foo:foo" ), equalTo( "org.bar:foo:1.0" ) );
        assertThat( p.get( "my.proj:core" ), equalTo( "org.proj:core:1.0" ) );
    }

}
