package com.redhat.rcm.version.util;

import javax.inject.Named;

public class AnnotationUtils
{

    private AnnotationUtils()
    {
    }

    public static String nameOf( final Object obj )
    {
        return nameOf( obj.getClass() );
    }

    public static String nameOf( final Class<?> cls )
    {
        final Named named = cls.getAnnotation( Named.class );
        if ( named == null )
        {
            return null;
        }

        final String name = named.value();
        if ( name == null || name.trim()
                                 .length() < 1 )
        {
            return null;
        }

        return name;
    }

}
