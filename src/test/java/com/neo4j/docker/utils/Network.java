package com.neo4j.docker.utils;

import java.io.IOException;
import java.net.ServerSocket;

import static java.lang.String.format;

public class Network
{
    public static int getUniqueHostPort() throws IOException
    {
        try ( ServerSocket socket = new ServerSocket( 0 ) )
        {
            socket.setReuseAddress( true );
            socket.close();
            return socket.getLocalPort();
        }
        catch ( IOException e )
        {
            throw new IOException( format( "Could not get a unique port : ", e.getMessage() ) );
        }
    }
}
