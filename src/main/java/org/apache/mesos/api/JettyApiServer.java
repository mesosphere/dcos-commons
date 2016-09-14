package org.apache.mesos.api;

import org.eclipse.jetty.server.Server;
import org.glassfish.jersey.jetty.JettyHttpContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Collection;

/**
 * A JettyApiServer takes a list of POJO JAX-RS Resources and serves them on the indicated port of 0.0.0.0.
 */
public class JettyApiServer {
    private final Server server;

    public JettyApiServer(int port, Collection<Object> resources) {
        ResourceConfig resourceConfig = new ResourceConfig();
        for (Object resource : resources) {
            resourceConfig.register(resource);
        }

        URI baseUri = UriBuilder
                .fromUri("http://0.0.0.0/")
                .port(port).build();

        this.server = JettyHttpContainerFactory.createServer(baseUri, resourceConfig);
    }

    public void start() throws Exception {
        try {
            server.start();
            server.dumpStdErr();
            server.join();
        } finally {
            server.destroy();
        }
    }

    public void stop() throws Exception {
        server.stop();
    }
}
