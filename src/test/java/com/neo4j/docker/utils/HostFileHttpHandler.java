package com.neo4j.docker.utils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;

/**
 * HttpHandler that responds to all http requests with the given file from the file system
 */
public class HostFileHttpHandler implements HttpHandler
{
    private final File file;
    private final String contentType;

    public HostFileHttpHandler( File fileToDownload, String contentType )
    {
        this.file = fileToDownload;
        this.contentType = contentType;
    }

    @Override
    public void handle( HttpExchange exchange ) throws IOException
    {
        exchange.getResponseHeaders().add( "Content-Type", contentType );
        exchange.sendResponseHeaders( HttpURLConnection.HTTP_OK, file.length() );
        Files.copy( this.file.toPath(), exchange.getResponseBody() );
        exchange.close();
    }
}