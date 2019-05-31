package utils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Neo4jVersion
{
    //public static final Neo4jVersion EXPECTED_NEO4J_VERSION = Neo4jVersion.fromVersionString( System.getenv( "NEO4J_VERSION" ) );
    //public static final Neo4jVersion LATEST_2X_VERSION = new Neo4jVersion(2,3,12);
    //public static final Neo4jVersion LATEST_32_VERSION = new Neo4jVersion(3,2,14);
    public static final Neo4jVersion NEO4J_VERSION_400 = new Neo4jVersion(4,0,0);

    public final int major;
    public final int minor;
    public final int patch;
    public final String label;

    public static Neo4jVersion fromVersionString(String version)
    {
        // Could be one of the forms:
        // A.B.C, A.B.C-alphaDD, A.B.C-betaDD, A.B.C-rcDD
        // (?<major>\d)\.(?<minor>\d)\.(?<patch>[\d]+)(?<label>-(alpha|beta|[Rr][Cc])[\d]{1,2})?
        Pattern pattern = Pattern.compile( "(?<major>\\d)\\.(?<minor>\\d)\\.(?<patch>[\\d]+)(?<label>-(alpha|beta|[Rr][Cc])[\\d]{1,2})?" );
        Matcher x = pattern.matcher( version );
        x.find();
        return new Neo4jVersion(
                Integer.parseInt(x.group("major")),
                Integer.parseInt(x.group("minor")),
                Integer.parseInt(x.group("patch")),
                (x.group("label") == null)? "" : x.group("label")
        );
    }

    public Neo4jVersion( int major, int minor, int patch )
    {
        this(major, minor, patch, "");
    }

    public Neo4jVersion( int major, int minor, int patch, String label )
    {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.label = label;
    }

    public String toAptReleaseVersion()
    {
        // every release after about February 2018 has the prefix "1:" in apt

        switch(major)
        {
        case 0: case 1: case 2:
                return toString();
        case 3:
            switch ( minor )
            {
            case 0:
                return toString();
            case 1:
                if(patch < 8) return toString();
                break;
            case 2:
                if(patch < 10) return toString();
                break;
            case 3:
                if(patch < 3) return toString();
                break;
            }
            break;
        }
        return "1:"+toString();
    }

    public String toYumReleaseVersion()
    {
        return toString();
    }

    public boolean isOlderThan( Neo4jVersion that )
    {
        if(this.major != that.major)
        {
            return (this.major < that.major);
        }
        else
        {
            if(this.minor != that.minor)
            {
                return (this.minor < that.minor);
            }
            else return (this.patch < that.patch);
        }
        // Not comparing the alpha/beta label because it's still the *same* major minor patch version and a very unlikely upgrade path
    }

    public boolean isNewerThan( Neo4jVersion that )
    {
        if(this.major != that.major)
        {
            return (this.major > that.major);
        }
        else
        {
            if(this.minor != that.minor)
            {
                return (this.minor > that.minor);
            }
            else return (this.patch > that.patch);
        }
        // Not comparing the alpha/beta label because it's still the *same* major minor patch version and a very unlikely upgrade path
    }

    public boolean isAtLeastVersion( Neo4jVersion that )
    {
        boolean isNewer = this.isNewerThan( that );
        if(!isNewer)
        {
            return major == that.major && minor == that.minor && patch == that.patch;
        }
        else return isNewer;
    }

    @Override
    public String toString()
    {
        return String.format( "%d.%d.%d%s", major, minor, patch, label );
    }

//    @Override
//    public boolean equals( Object o )
//    {
//        if ( this == o )
//        {
//            return true;
//        }
//        if ( o == null || getClass() != o.getClass() )
//        {
//            return false;
//        }
//
//        Neo4jVersion that = (Neo4jVersion) o;
//        return major == that.major && minor == that.minor && patch == that.patch && label.equals( that.label );
//    }
}
