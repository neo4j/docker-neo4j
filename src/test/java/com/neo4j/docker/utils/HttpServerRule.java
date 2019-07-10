package com.neo4j.docker.utils;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.rules.ExternalResource;

import java.net.InetSocketAddress;

/**
 * Runs a HTTP Server with to allow integration testing
 */
public class HttpServerRule extends ExternalResource
{
    public final int PORT = 3000;
    private HttpServer server;

    @Override
    protected void before() throws Throwable
    {
        server = HttpServer.create( new InetSocketAddress( PORT ), 0 );
        server.setExecutor( null ); // creates a default executor
        server.start();
    }

    @Override
    protected void after()
    {
        if ( server != null )
        {
            server.stop( 0 ); // doesn't wait all current exchange handlers complete
        }
    }

    // Register a handler to provide desired behaviour on a specific uri path
    public void registerHandler( String uriToHandle, HttpHandler httpHandler )
    {
        if (!uriToHandle.startsWith( "/" )){
            uriToHandle = '/' + uriToHandle;
        }
        server.createContext( uriToHandle, httpHandler );
    }
}