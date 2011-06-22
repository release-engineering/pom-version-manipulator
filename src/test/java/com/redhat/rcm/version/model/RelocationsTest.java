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

package com.redhat.rcm.version.model;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import com.redhat.rcm.version.VManException;

import java.io.File;
import java.util.Map;

public class RelocationsTest
{

    @Test
    public void checkAddRelocations_HandlingOfSpaceNewlineComma()
        throws VManException
    {
        final File f = new File( "." );
        final Relocations relocations = new Relocations();

        final String inGA1 = "org.foo:foo";
        final String outGA1 = "org.bar:foo";

        final String inGA2 = "my.proj:core";
        final String outGA2 = "org.proj:core";

        relocations.addBomRelocations( f, inGA1 + "=" + outGA1 + " \n," + inGA2 + "=" + outGA2 );

        final Map<VersionlessProjectKey, VersionlessProjectKey> r = relocations.getRelocationsByFile().get( f );

        assertThat( r.get( new VersionlessProjectKey( "org.foo", "foo" ) ),
                    equalTo( new VersionlessProjectKey( "org.bar", "foo" ) ) );
        assertThat( r.get( new VersionlessProjectKey( "my.proj", "core" ) ),
                    equalTo( new VersionlessProjectKey( "org.proj", "core" ) ) );
    }

    @Test
    public void checkAddRelocations_HandlingOfSpaceCommaNewlineTab()
        throws VManException
    {
        final File f = new File( "." );
        final Relocations relocations = new Relocations();

        final String inGA1 = "org.foo:foo";
        final String outGA1 = "org.bar:foo";

        final String inGA2 = "my.proj:core";
        final String outGA2 = "org.proj:core";

        relocations.addBomRelocations( f, inGA1 + "=" + outGA1 + " ,\n\t" + inGA2 + "=" + outGA2 );

        final Map<VersionlessProjectKey, VersionlessProjectKey> r = relocations.getRelocationsByFile().get( f );

        assertThat( r.get( new VersionlessProjectKey( "org.foo", "foo" ) ),
                    equalTo( new VersionlessProjectKey( "org.bar", "foo" ) ) );
        assertThat( r.get( new VersionlessProjectKey( "my.proj", "core" ) ),
                    equalTo( new VersionlessProjectKey( "org.proj", "core" ) ) );
    }

}
