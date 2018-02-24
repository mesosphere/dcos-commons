package com.mesosphere.sdk.scheduler;

import com.codahale.metrics.jetty9.InstrumentedHandler;
import com.google.common.annotations.VisibleForTesting;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.glassfish.jersey.jetty.JettyHttpContainerFactory;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

/**
 * The SchedulerApiServer runs the Jetty {@link Server} that exposes the Scheduler's API.
 */
public class SchedulerApiServer implements MesosEventClient.ResourceServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerApiServer.class);

    private final int port;
    private final Server server;
    private final Duration startTimeout;
    private final ServletHandler servletHandler;
    private final NamespacedApiServlet namespacedServlet;

    public static SchedulerApiServer start(SchedulerConfig schedulerConfig, Runnable startedCallback) {
        SchedulerApiServer apiServer = new SchedulerApiServer(schedulerConfig);
        apiServer.start(new AbstractLifeCycle.AbstractLifeCycleListener() {
            @Override
            public void lifeCycleStarted(LifeCycle event) {
                startedCallback.run();
            }
        });
        return apiServer;
    }

    @VisibleForTesting
    SchedulerApiServer(SchedulerConfig schedulerConfig) {
        this.port = schedulerConfig.getApiServerPort();
        this.server = JettyHttpContainerFactory.createServer(
                UriBuilder.fromUri("http://0.0.0.0/").port(this.port).build(),
                false /* don't start yet. wait for start() call below. */);
        this.startTimeout = schedulerConfig.getApiServerInitTimeout();
        this.namespacedServlet = new NamespacedApiServlet();

        // Collect basic metrics on queries, and store those metrics in the registry:
        // TODO reimplement it within NamespacedServlet with the metrics we actually want (i.e. more granular)
        final InstrumentedHandler instrumentedHandler = new InstrumentedHandler(Metrics.getRegistry());
        this.servletHandler = new ServletHandler();
        servletHandler.setEnsureDefaultServlet(false);
        instrumentedHandler.setHandler(servletHandler);
        // Expose the registry's metrics at these endpoints:
        Metrics.configureMetricsEndpoints(servletHandler, "/v1/metrics", "/v1/metrics/prometheus");
        ServletHolder namespacedServletHolder = new ServletHolder(namespacedServlet);
        servletHandler.addServletWithMapping(namespacedServletHolder, "/*");

        server.setHandler(instrumentedHandler);
    }

    /**
     * Launches the API server on a separate thread.
     *
     * @param listener A listener object which will be notified when the underlying server changes state
     */
    private void start(LifeCycle.Listener listener) {
        if (server.isStarted()) {
            throw new IllegalStateException("Already started");
        }
        server.addLifeCycleListener(listener);

        final Timer startTimer = new Timer("API-start-timeout");
        startTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!server.isStarted()) {
                    LOGGER.error("API Server failed to start at port {} within {}ms", port, startTimeout.toMillis());
                    SchedulerUtils.hardExit(SchedulerErrorCode.API_SERVER_ERROR);
                }
            }
        }, startTimeout.toMillis());

        Runnable runServerCallback = new Runnable() {
            public void run() {
                try {
                    LOGGER.info("Starting API server at port {}", port);
                    server.start();
                    int localPort = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
                    LOGGER.info("API server started at port {}", localPort);
                    startTimer.cancel();
                    server.join();
                } catch (Exception e) {
                    LOGGER.error(String.format("API server at port %d failed with exception: ", port), e);
                    SchedulerUtils.hardExit(SchedulerErrorCode.API_SERVER_ERROR);
                } finally {
                    LOGGER.info("API server at port {} exiting", port);
                    try {
                        server.destroy();
                    } catch (Exception e) {
                        LOGGER.error(String.format("Failed to stop API server at port %d with exception: ", port), e);
                    }
                }
            }
        };

        new Thread(runServerCallback).start();
    }

    /**
     * Adds the annotated {@code resources} to be served under the specified namespace mapping.
     *
     * NOTE: When there's a conflict, jetty behavior is to give priority to more explicit namespaces then less explicit
     * ones. So for example a resource at "/foo/bar" under namespace "" gets lower priority than a resource at "/bar"
     * under namespace "/foo".
     *
     * @param namespace The namespace to use for these resources. Cannot start with a slash. For example, a namespace of
     *                  "v1" for a resource annotated with "foo" will result in that resource being served as "v1/foo".
     *                  An empty string may be used for the root namespace.
     * @param resources Resources to be served within the specified namespace. Does nothing if the list of resources is
     *                  empty.
     * @throws IllegalArgumentException if the specified namespace is already present
     */
    public synchronized void addResources(String namespace, Collection<Object> resources) {
        if (resources.isEmpty()) {
            return;
        }
        LOGGER.info("Adding {} resource{} under namespace '{}': {}",
                resources.size(),
                resources.size() == 1 ? "" : "s",
                namespace,
                resources.stream().map(o -> o.getClass().getSimpleName()).collect(Collectors.toList()));

        if (namespace.indexOf("/") >= 0) {
            throw new IllegalArgumentException(String.format("Namespace cannot contain '/': '%s'", namespace));
        }

        ServletContainer sc = new ServletContainer(new ResourceConfig(MultiPartFeature.class).registerInstances(resources));
        try {
            sc.init(getBogusConfig(namespace));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        namespacedServlet.addServlet(namespace, sc);
    }

    /**
     * Removes resources which were previously added under the specified namespace, or does nothing if that namespace
     * does not exist.
     *
     * @param namespace The namespace to be removed.
     */
    public synchronized void removeResources(String namespace) {
        namespacedServlet.removeServlet(namespace);
    }

    /**
     * Returns the server endpoint. Mainly used for testing.
     *
     * @throws IllegalStateException if the server is not running
     */
    @VisibleForTesting
    URI getURI() {
        if (!server.isRunning()) {
            throw new IllegalStateException("Server is not running");
        }
        return server.getURI();
    }

    /**
     * Stops the server. Mainly used for testing.
     */
    @VisibleForTesting
    void stop() throws Exception {
        server.stop();
    }

    /**
     * Workaround for Jetty's terrible API.
     */
    private static ServletConfig getBogusConfig(String namespace) {
        return new ServletConfig() {

            @Override
            public String getServletName() {
                return namespace;
            }

            @Override
            public ServletContext getServletContext() {
                return new ContextHandler.NoContext();
            }

            @Override
            public String getInitParameter(String name) {
                return null;
            }

            @Override
            public Enumeration<String> getInitParameterNames() {
                return Collections.emptyEnumeration();
            }
        };
    }
}
