package com.redhat.rcm.version.util;

public class ObjectToString<T>
    implements ToStringProcessor<T>
{

    @Override
    public String render( final T value )
    {
        return String.valueOf( value );
    }

}
