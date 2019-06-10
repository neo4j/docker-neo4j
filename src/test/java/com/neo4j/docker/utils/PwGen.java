package com.neo4j.docker.utils;

import java.security.SecureRandom;

/**
 * Password generator.
 */
public final class PwGen extends Object {

    /**
     * Character set containing numeric and uppercase letters.
     */
    public static final char[] NUMBER_UPPER={
            '2', '3', '4', '5', '6', '7', '8', '9',
            'B', 'C', 'D', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N',
            'P', 'Q', 'R', 'S', 'T', 'V', 'W', 'X', 'Y', 'Z'
    };

    /**
     * Character set containing numeric and lowercase characters.
     */
    public static final char[] NUMBER_LOWER={
            '2', '3', '4', '5', '6', '7', '8', '9',
            'b', 'c', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm', 'n',
            'p', 'q', 'r', 's', 't', 'v', 'w', 'x', 'y', 'z'
    };

    /**
     * Character set containing numeric, upper, and lowercase letters.
     */
    public static final char[] NUMBER_UPPER_LOWER={
            '2', '3', '4', '5', '6', '7', '8', '9',
            'B', 'C', 'D', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N',
            'P', 'Q', 'R', 'S', 'T', 'V', 'W', 'X', 'Y', 'Z',
            'b', 'c', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm', 'n',
            'p', 'q', 'r', 's', 't', 'v', 'w', 'x', 'y', 'z'
    };

    /**
     * Get a random string at the given length using the NUMBER_UPPER set.
     */
    public static  String getPass(int length) {
        return(getPass(length, NUMBER_LOWER));
    }

    /**
     * Get a random string with the given length and character set.
     */
    public static  String getPass(int length, char[] charset) {
        SecureRandom sr=new SecureRandom();
        StringBuilder sb=new StringBuilder(length);
        for(int i=0; i<length; i++) {
            // Stick a random character in our result.
            sb.append(charset[sr.nextInt(charset.length)]);
        }
        return(sb.toString());
    }

}
