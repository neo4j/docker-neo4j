package com.neo4j.docker.utils;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.net.InetSocketAddress;

/**
 * Runs a HTTP Server with to allow integration testing
 */
public class HttpServerTestExtension implements AfterEachCallback, BeforeEachCallback
{
    public final int PORT = 3000;
    private HttpServer server;

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception
    {
        server = HttpServer.create( new InetSocketAddress( PORT ), 0 );
        server.setExecutor( null ); // creates a default executor
        server.start();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception
    {
        if ( server != null )
        {
            server.stop( 5 ); // waits up to 5 seconds to stop serving http requests
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

    public void unregisterEndpoint(String endpoint)
    {
        if (!endpoint.startsWith( "/" )){
            endpoint = '/' + endpoint;
        }
        try
        {
            server.removeContext(endpoint);
        }
        catch (IllegalArgumentException iex)
        {
            // there was nothing registered to that endpoint so action is a NOP.
        }
    }
}