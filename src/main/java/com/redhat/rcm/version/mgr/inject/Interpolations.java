package com.redhat.rcm.version.mgr.inject;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import com.redhat.rcm.version.model.Project;

public enum Interpolations
{

    GROUP_ID( "${project.groupId}" )
    {
        @Override
        protected String getPropertyValue( final Project project )
        {
            return project.getGroupId();
        }
    },
    PARENT_GROUP_ID( "${project.parent.groupId}" )
    {
        @Override
        protected String getPropertyValue( final Project project )
        {
            return project.getParent() == null ? null : project.getParent().getGroupId();
        }
    },
    ARTIFACT_ID( "${project.artifactId}" )
    {
        @Override
        protected String getPropertyValue( final Project project )
        {
            return project.getArtifactId();
        }
    },
    VERSION( "${project.version}" )
    {
        @Override
        protected String getPropertyValue( final Project project )
        {
            return project.getVersion();
        }
    },
    PARENT_VERSION( "${project.parent.version}" )
    {
        @Override
        protected String getPropertyValue( final Project project )
        {
            return project.getParent() == null ? null : project.getParent().getVersion();
        }
    },
    PROPERTIES( "${*}" ) // ALWAYS LAST IN THE LINEUP!
    {
        @Override
        protected String getPropertyValue( final Project project )
        {
            return null;
        }

        @Override
        public StringBuilder interpolate( final StringBuilder src, final Project project )
        {
            Properties p = project.getModel().getProperties();
            if ( p == null || p.isEmpty() )
            {
                return src;
            }

            Set<String> seen = new HashSet<String>();
            int changes = 0;
            do
            {
                changes = 0;

                int idx = -1;
                int last = 0;
                while ( ( idx = src.indexOf( "${", last ) ) > -1 )
                {
                    int end = src.indexOf( "}", idx + 1 );
                    if ( end < 0 )
                    {
                        break;
                    }

                    String property = src.substring( idx + 2, end );
                    if ( seen.contains( property ) )
                    {
                        // we've seen this before; don't do anything with it.
                    }
                    else
                    {
                        seen.add( property );
                        String value = p.getProperty( property );
                        if ( value != null )
                        {
                            src.replace( idx, end + 1, value );
                            changes++;
                        }
                    }

                    last = end + 1;
                    if ( last == src.length() )
                    {
                        break;
                    }
                }
            }
            while ( changes > 0 );

            return src;
        }

    };

    protected final String property;

    private Interpolations( final String property )
    {
        this.property = property;
    }

    protected abstract String getPropertyValue( Project project );

    public StringBuilder interpolate( final StringBuilder src, final Project project )
    {
        final String value = getPropertyValue( project );
        if ( value == null )
        {
            return src;
        }

        int idx = -1;
        int last = 0;

        final int len = property.length();
        while ( ( idx = src.indexOf( property, last ) ) > -1 )
        {
            src.replace( idx, idx + len, value );
            last = idx + len + 1;
            if ( last >= src.length() )
            {
                break;
            }
        }

        return src;
    }

    public static String interpolate( final String value, final Project project )
    {
        StringBuilder result = new StringBuilder( value );
        for ( Interpolations interp : values() )
        {
            interp.interpolate( result, project );
        }

        return result.toString();
    }

}
