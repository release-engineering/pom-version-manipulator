package com.redhat.rcm.version.mgr;

import java.util.List;

import com.redhat.rcm.version.VManException;

public class MultiVManException
    extends VManException
{

    private List<Throwable> errors;

    public MultiVManException( final String message, final List<Throwable> errors, final Object... params )
    {
        super( message, params );
        this.errors = errors;
    }

    private static final long serialVersionUID = 1L;

    @Override
    public synchronized String getMessage()
    {
        final StringBuilder msg = new StringBuilder();
        msg.append( super.getMessage() );

        msg.append( "\n\n" )
           .append( errors.size() )
           .append( " associated exceptions:\n-------------------------------------------" );

        int idx = 0;
        for ( final Throwable error : errors )
        {
            msg.append( "\n\n  " )
               .append( idx++ )
               .append( ": " )
               .append( error.getMessage() );
        }

        return msg.toString();
    }

    public List<Throwable> getErrors()
    {
        return errors;
    }

}
