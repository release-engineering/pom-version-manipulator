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
