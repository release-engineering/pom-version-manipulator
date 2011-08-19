package com.redhat.rcm.version.util;

public class ObjectToStringProcessor<T>
    implements ToStringProcessor<T>
{

    @Override
    public String render( final T value )
    {
        return String.valueOf( value );
    }

}
