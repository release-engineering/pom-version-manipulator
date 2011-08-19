package com.redhat.rcm.version.util;

public interface ToStringProcessor<T>
{

    String render( T value );

}
