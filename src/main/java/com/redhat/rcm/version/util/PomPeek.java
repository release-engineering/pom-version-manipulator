package com.redhat.rcm.version.util;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.maven.mae.project.key.FullProjectKey;
import org.commonjava.util.logging.Logger;

public class PomPeek
{

    private static final String PROJECT = "project";

    private static final String RELATIVE_PATH = "relativePath";

    private static final String MODEL_VERSION = "modelVersion";

    private static final String PARENT = "parent";

    private static final String ARTIFACT_ID = "artifactId";

    private static final String GROUP_ID = "groupId";

    private static final String VERSION = "version";

    private static final String PACKAGING = "packaging";

    private static final String NAME = "name";

    private static final String DESCRIPTION = "description";

    private static final String INCEPTION_YEAR = "inceptionYear";

    private static final String URL = "url";

    private static final Set<String> HEADER_ELEMENTS = Collections.unmodifiableSet( new HashSet<String>()
    {
        private static final long serialVersionUID = 1L;

        {
            add( PARENT );
            add( ARTIFACT_ID );
            add( GROUP_ID );
            add( VERSION );

            // chaff, but we need these to keep us in the running for a reasonable coordinate...
            // these, with the ones above, are still sort of considered in the 
            // "header" of the pom.xml...that is, things that generally come first.
            add( PROJECT );
            add( MODEL_VERSION );
            add( PACKAGING );
            add( NAME );
            add( DESCRIPTION );
            add( RELATIVE_PATH );
            add( INCEPTION_YEAR );
            add( URL );
        }
    } );

    private final Logger logger = new Logger( getClass() );

    private FullProjectKey key;

    private String parentGid;

    private String parentVer;

    private String gid;

    private String aid;

    private String ver;

    private transient boolean parsingParent = false;

    public PomPeek( final File pom )
    {
        parseCoordElements( pom );

        final String v = isNotEmpty( ver ) ? ver : parentVer;
        final String g = isNotEmpty( gid ) ? gid : parentGid;

        if ( isValidArtifactId( aid ) && isValidGroupId( g ) && isValidVersion( v ) )
        {
            key = new FullProjectKey( g, aid, v );
        }
        else
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

            all: while ( xml.hasNext() )
            {
                final int evt = xml.next();
                switch ( evt )
                {
                    case START_ELEMENT:
                    {
                        if ( !inHeader( xml ) )
                        {
                            logger.info( "Hit non-header element: %s. STOP xml processing.", xml.getLocalName() );
                            break all;
                        }

                        processElement( xml );
                        break;
                    }
                    case END_ELEMENT:
                    {
                        if ( "parent".equals( xml.getLocalName() ) )
                        {
                            parsingParent = false;
                        }
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

    private boolean inHeader( final XMLStreamReader xml )
    {
        return HEADER_ELEMENTS.contains( xml.getLocalName() );
    }

    private void processElement( final XMLStreamReader xml )
        throws XMLStreamException
    {
        final String lname = xml.getLocalName();
        if ( PARENT.equals( lname ) )
        {
            parsingParent = true;
        }
        else if ( GROUP_ID.equals( lname ) )
        {
            if ( parsingParent )
            {
                parentGid = xml.getElementText();
            }
            else
            {
                gid = xml.getElementText();
            }
        }
        else if ( VERSION.equals( lname ) )
        {
            if ( parsingParent )
            {
                parentVer = xml.getElementText();
            }
            else
            {
                ver = xml.getElementText();
            }
        }
        else if ( ARTIFACT_ID.equals( lname ) && !parsingParent )
        {
            aid = xml.getElementText();
        }
    }

}
