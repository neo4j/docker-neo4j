package com.neo4j.docker.utils;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Map;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.Testcontainers;

/**
 * Runs a HTTP Server with to allow integration testing
 */
public class HttpServerTestExtension implements AfterEachCallback, BeforeEachCallback {
    // The image's neo4j-plugins.json points the "_testing" plugin at host.testcontainers.internal:3000,
    // so the port seen from inside the container must stay 3000.
    private static final int CONTAINER_PORT = 3000;

    // On the host we bind an ephemeral port so that parallel surefire forks do not collide.
    // Testcontainers forwards a container port only once per JVM, so the same host port is reused
    // for every test in this JVM.
    private static int hostPort = 0;
    private HttpServer server;

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        server = HttpServer.create(new InetSocketAddress(hostPort), 0);
        hostPort = server.getAddress().getPort();
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        if (server != null) {
            server.stop(5); // waits up to 5 seconds to stop serving http requests
        }
    }

    /** Makes this server reachable from containers at {@link #getContainerAccessibleUrl()}. Call before starting a container. */
    public void exposeToContainers() {
        Testcontainers.exposeHostPorts(Map.of(hostPort, CONTAINER_PORT));
    }

    public String getContainerAccessibleUrl() {
        return "http://host.testcontainers.internal:" + CONTAINER_PORT + "/";
    }

    // Register a handler to provide desired behaviour on a specific uri path
    public void registerHandler(String uriToHandle, HttpHandler httpHandler) {
        if (!uriToHandle.startsWith("/")) {
            uriToHandle = '/' + uriToHandle;
        }
        server.createContext(uriToHandle, httpHandler);
    }

    public void unregisterEndpoint(String endpoint) {
        if (!endpoint.startsWith("/")) {
            endpoint = '/' + endpoint;
        }
        try {
            server.removeContext(endpoint);
        } catch (IllegalArgumentException iex) {
            // there was nothing registered to that endpoint so action is a NOP.
        }
    }
}
