package com.mesosphere.sdk.framework;

import com.codahale.metrics.jetty9.InstrumentedHandler;
import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.Metrics;
import com.mesosphere.sdk.scheduler.SchedulerConfig;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.glassfish.jersey.jetty.JettyHttpContainerFactory;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;

import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The {@link ApiServer} runs the Jetty {@link Server} that exposes the Scheduler's API.
 */
public class ApiServer {

    private static final Logger LOGGER = LoggingUtils.getLogger(ApiServer.class);

    private final int port;
    private final Server server;
    private final Duration startTimeout;

    private Thread serverThread;

    public static ApiServer start(
            SchedulerConfig schedulerConfig, Collection<Object> resources, Runnable startedCallback) {
        ApiServer apiServer = new ApiServer(schedulerConfig, resources);
        apiServer.start(new AbstractLifeCycle.AbstractLifeCycleListener() {
            @Override
            public void lifeCycleStarted(LifeCycle event) {
                startedCallback.run();
            }
        });
        return apiServer;
    }

    @VisibleForTesting
    ApiServer(SchedulerConfig schedulerConfig, Collection<Object> resources) {
        this.port = schedulerConfig.getApiServerPort();
        this.server = JettyHttpContainerFactory.createServer(
                UriBuilder.fromUri("http://0.0.0.0/").port(this.port).build(),
                new ResourceConfig(MultiPartFeature.class).registerInstances(new HashSet<>(resources)),
                false /* don't start yet. wait for start() call below. */);
        this.startTimeout = schedulerConfig.getApiServerInitTimeout();

        ServletContextHandler context = new ServletContextHandler();

        // Serve metrics registry content at these paths:
        Metrics.configureMetricsEndpoints(context, "/v1/metrics", "/v1/metrics/prometheus");
        // Serve resources at their declared paths relative to root:
        context.addServlet(new ServletHolder(new ServletContainer(
                new ResourceConfig(MultiPartFeature.class).registerInstances(new HashSet<>(resources)))),
                "/*");

        // Passthru handler: Collect basic metrics on queries, and store those metrics in the registry
        // TODO(nickbp): reimplement InstrumentedHandler with better/more granular metrics (e.g. resource being queried)
        final InstrumentedHandler instrumentedHandler = new InstrumentedHandler(Metrics.getRegistry());
        instrumentedHandler.setHandler(context);

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
                    ProcessExit.exit(ProcessExit.API_SERVER_ERROR);
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
                    ProcessExit.exit(ProcessExit.API_SERVER_ERROR, e);
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

        serverThread = new Thread(runServerCallback);
        serverThread.start();
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
     * Waits for the server to stop. If the server is not stopped, this waits forever.
     *
     * <p>Useful for keeping the scheduler alive while only running the HTTP service.
     */
    public void join() {
        while (true) {
            try {
                serverThread.join();
                break;
            } catch (InterruptedException e) {
                LOGGER.error("Interrupted while waiting for HTTP server to join. Retrying wait.", e);
            }
        }
    }

    /**
     * Stops the server. Used for tests.
     */
    @VisibleForTesting
    void stop() throws Exception {
        server.stop();
    }
}
