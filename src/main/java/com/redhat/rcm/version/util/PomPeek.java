package com.redhat.rcm.version.util;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.join;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.maven.mae.project.key.FullProjectKey;
import org.commonjava.util.logging.Logger;

public class PomPeek
{

    private static final String G = "g";

    private static final String A = "a";

    private static final String V = "v";

    private static final String PG = "pg";

    private static final String PV = "pv";

    private static final Map<String, String> CAPTURED_PATHS = new HashMap<String, String>()
    {
        private static final long serialVersionUID = 1L;

        {
            put( "project:groupId", G );
            put( "project:artifactId", A );
            put( "project:version", V );
            put( "project:parent:groupId", PG );
            put( "project:parent:version", PV );
        }
    };

    private final Logger logger = new Logger( getClass() );

    private FullProjectKey key;

    private final Map<String, String> elementValues = new HashMap<String, String>();

    public PomPeek( final File pom )
    {
        parseCoordElements( pom );

        if ( !createCoordinate() )
        {
            logger.warn( "Could not peek at POM coordinate for: %s. "
                + "This POM will NOT be available as an ancestor to other models during effective-model building.", pom );
        }
    }

    private boolean isValidVersion( final String version )
    {
        if ( isEmpty( version ) )
        {
            return false;
        }

        if ( "version".equals( version ) )
        {
            return false;
        }

        if ( "parentVersion".equals( version ) )
        {
            return false;
        }

        return true;
    }

    private boolean isValidGroupId( final String groupId )
    {
        if ( isEmpty( groupId ) )
        {
            return false;
        }

        if ( groupId.contains( "${" ) )
        {
            return false;
        }

        if ( "parentGroupId".equals( groupId ) )
        {
            return false;
        }

        if ( "groupId".equals( groupId ) )
        {
            return false;
        }

        return true;
    }

    private boolean isValidArtifactId( final String artifactId )
    {
        if ( isEmpty( artifactId ) )
        {
            return false;
        }

        if ( artifactId.contains( "${" ) )
        {
            return false;
        }

        if ( "parentArtifactId".equals( artifactId ) )
        {
            return false;
        }

        if ( "artifactId".equals( artifactId ) )
        {
            return false;
        }

        return true;
    }

    public FullProjectKey getKey()
    {
        return key;
    }

    private void parseCoordElements( final File pom )
    {
        Reader reader = null;
        XMLStreamReader xml = null;
        try
        {
            reader = new FileReader( pom );
            xml = XMLInputFactory.newFactory()
                                 .createXMLStreamReader( reader );

            final Stack<String> path = new Stack<String>();
            while ( xml.hasNext() )
            {
                final int evt = xml.next();
                switch ( evt )
                {
                    case START_ELEMENT:
                    {
                        path.push( xml.getLocalName() );
                        if ( captureValue( path, xml ) )
                        {
                            // seems like xml.getElementText() traverses the END_ELEMENT event...
                            path.pop();
                        }

                        if ( foundPreferredValues() )
                        {
                            return;
                        }
                        break;
                    }
                    case END_ELEMENT:
                    {
                        path.pop();
                        break;
                    }
                    default:
                    {
                    }
                }
            }
        }
        catch ( final IOException e )
        {
            logger.warn( "Failed to peek at POM coordinate for: %s. Reason: %s\n"
                             + "This POM will NOT be available as an ancestor to other models during effective-model building.",
                         e,
                         pom, e.getMessage() );
        }
        catch ( final XMLStreamException e )
        {
            logger.warn( "Failed to peek at POM coordinate for: %s. Reason: %s\n"
                             + "This POM will NOT be available as an ancestor to other models during effective-model building.",
                         e,
                         pom, e.getMessage() );
        }
        catch ( final FactoryConfigurationError e )
        {
            logger.warn( "Failed to peek at POM coordinate for: %s. Reason: %s\n"
                             + "This POM will NOT be available as an ancestor to other models during effective-model building.",
                         e,
                         pom, e.getMessage() );
        }
        finally
        {
            if ( xml != null )
            {
                try
                {
                    xml.close();
                }
                catch ( final XMLStreamException e )
                {
                }
            }

            closeQuietly( reader );
        }
    }

    private boolean foundPreferredValues()
    {
        return elementValues.containsKey( A ) && elementValues.containsKey( G ) && elementValues.containsKey( V );
    }

    private boolean captureValue( final Stack<String> path, final XMLStreamReader xml )
        throws XMLStreamException
    {
        final String pathStr = join( path, ":" );
        final String key = CAPTURED_PATHS.get( pathStr );
        if ( key != null )
        {
            elementValues.put( key, xml.getElementText()
                                       .trim() );

            return true;
        }

        return false;
    }

    private boolean createCoordinate()
    {
        String v = elementValues.get( V );
        if ( isEmpty( v ) )
        {
            v = elementValues.get( PV );
        }

        String g = elementValues.get( G );
        if ( isEmpty( g ) )
        {
            g = elementValues.get( PG );
        }

        final String a = elementValues.get( A );

        if ( isValidArtifactId( a ) && isValidGroupId( g ) && isValidVersion( v ) )
        {
            key = new FullProjectKey( g, a, v );
            return true;
        }

        return false;
    }

}
