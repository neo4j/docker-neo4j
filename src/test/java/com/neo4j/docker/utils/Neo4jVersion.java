package com.neo4j.docker.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Neo4jVersion
{
    public static final Neo4jVersion NEO4J_VERSION_400 = new Neo4jVersion( 4, 0, 0 );
    public static final Neo4jVersion NEO4J_VERSION_440 = new Neo4jVersion( 4, 4, 0 );
    public static final Neo4jVersion NEO4J_VERSION_500 = new Neo4jVersion( 5, 0, 0 );
    public static final Neo4jVersion NEO4J_VERSION_527 = new Neo4jVersion( 5, 27, 0 );

    public final int major;
    public final int minor;
    public final int patch;
    public final String label;

    public static Neo4jVersion fromVersionString( String version )
    {
        Pattern pattern = Pattern.compile( "(?<major>[\\d]+)\\.(?<minor>[\\d]+)\\.(?<patch>[\\d]+)(?<label>-(.*))?" );
        Matcher x = pattern.matcher( version );
        x.find();

        return new Neo4jVersion(
                Integer.parseInt( x.group( "major" ) ),
                Integer.parseInt( x.group( "minor" ) ),
                Integer.parseInt( x.group( "patch" ) ),
                (x.group( "label" ) == null) ? "" : x.group( "label" )
        );
    }

    public static String makeVersionString(int major, int minor)
    {
        if ( major > 2023 )
        {
            return String.format( "%d.%02d", major, minor);
        }
        return String.format( "%d.%d", major, minor);
    }

    public static String makeVersionString(int major, int minor, int patch)
    {
        return makeVersionString( major, minor) + String.format(".%d", patch);
    }

    public Neo4jVersion( int major, int minor, int patch )
    {
        this( major, minor, patch, "" );
    }

    public Neo4jVersion( int major, int minor, int patch, String label )
    {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.label = label;
    }

    public boolean isNewerThan(Neo4jVersion that)
    {
        if ( this.major != that.major )
        {
            return (this.major > that.major);
        }
        else
        {
            if ( this.minor != that.minor )
            {
                return (this.minor > that.minor);
            }
            else
            {
                return (this.patch > that.patch);
            }
        }
        // Not comparing the alpha/beta label because it's still the *same* major minor patch version and a very unlikely upgrade path
    }

    public boolean isOlderThan( Neo4jVersion that )
    {
        return !isAtLeastVersion( that );
    }

    public boolean isAtLeastVersion( Neo4jVersion that )
    {
        boolean isNewer = this.isNewerThan( that );
        if ( !isNewer )
        {
            return isEqual( that );
        }
        else
        {
            return true;
        }
    }

    public boolean isEqual( Neo4jVersion that )
    {
        return (major == that.major) && (minor == that.minor) && (patch == that.patch);
    }

    @Override
    public String toString()
    {
        return makeVersionString( major, minor, patch ) + label;
    }

    public String toReleaseString()
    {
        return makeVersionString( major, minor, patch );
    }

    @Override
    public boolean equals( Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }

        return isEqual( (Neo4jVersion) o );
    }
}
