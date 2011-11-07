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

package com.redhat.rcm.version.util;

import java.util.Collection;

public class CollectionToString<T>
{

    private final Collection<T> coll;

    private final ToStringProcessor<T> itemProc;

    public CollectionToString( final Collection<T> coll )
    {
        this.coll = coll;
        this.itemProc = new ObjectToString<T>();
    }

    public CollectionToString( final Collection<T> coll, final ToStringProcessor<T> itemProc )
    {
        this.coll = coll;
        this.itemProc = itemProc;
    }

    @Override
    public String toString()
    {
        if ( coll == null || coll.isEmpty() )
        {
            return "-NONE-";
        }
        else
        {
            StringBuilder sb = new StringBuilder();
            for ( T item : coll )
            {
                if ( sb.length() > 0 )
                {
                    sb.append( '\n' );
                }

                sb.append( itemProc.render( item ) );
            }

            return sb.toString();
        }
    }

}
