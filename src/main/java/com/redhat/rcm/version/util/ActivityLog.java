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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ActivityLog
    implements Iterable<String>
{

    private final List<LogItem> items = new ArrayList<LogItem>();

    public void add( final String format, final Object... params )
    {
        items.add( new LogItem( format, params ) );
    }

    @Override
    public Iterator<String> iterator()
    {
        return new ItemIterator( items );
    }

    private static final class LogItem
    {
        private final String format;

        private final Object[] params;

        private String formatted;

        LogItem( final String format, final Object... params )
        {
            this.format = format;
            this.params = params;
        }

        @Override
        public String toString()
        {
            if ( formatted == null )
            {
                if ( params == null || params.length < 1 )
                {
                    formatted = format;
                }
                else
                {
                    final String original = formatted;
                    try
                    {
                        formatted = String.format( format, params );
                    }
                    catch ( final Error e )
                    {
                    }
                    catch ( final RuntimeException e )
                    {
                    }
                    catch ( final Exception e )
                    {
                    }

                    if ( formatted == null || original == formatted )
                    {
                        try
                        {
                            formatted = MessageFormat.format( format, params );
                        }
                        catch ( final Error e )
                        {
                            formatted = format;
                            throw e;
                        }
                        catch ( final RuntimeException e )
                        {
                            formatted = format;
                            throw e;
                        }
                        catch ( final Exception e )
                        {
                            formatted = format;
                        }
                    }
                }
            }

            return formatted;
        }
    }

    private static final class ItemIterator
        implements Iterator<String>
    {
        private final Iterator<LogItem> items;

        ItemIterator( final List<LogItem> items )
        {
            this.items = new ArrayList<LogItem>( items ).iterator();
        }

        @Override
        public boolean hasNext()
        {
            return items.hasNext();
        }

        @Override
        public String next()
        {
            return items.next().toString();
        }

        @Override
        public void remove()
        {
        }
    }

}
