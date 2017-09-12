package com.mesosphere.sdk.scheduler;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.LifeCycle;
import org.glassfish.jersey.jetty.JettyHttpContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;

import javax.ws.rs.core.UriBuilder;

/**
 * The SchedulerApiServer runs the Jetty {@link Server} that exposes the Scheduler's API.
 */
public class SchedulerApiServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerApiServer.class);

    private final Server server;
    private final Timer startTimer;
    private final Duration startTimeout;

    public SchedulerApiServer(SchedulerFlags schedulerFlags, Collection<Object> resources) {
        ResourceConfig resourceConfig = new ResourceConfig();
        for (Object resource : resources) {
            resourceConfig.register(resource);
        }
        this.server = JettyHttpContainerFactory.createServer(
                UriBuilder.fromUri("http://0.0.0.0/").port(schedulerFlags.getApiServerPort()).build(), resourceConfig);
        this.startTimer = new Timer();
        this.startTimeout = schedulerFlags.getApiServerInitTimeout();
    }

    /**
     * Launches the API server on a separate thread.
     */
    public void start(LifeCycle.Listener listener) {
        if (server.isStarted()) {
            throw new IllegalStateException("Already started");
        }
        server.addLifeCycleListener(listener);

        startTimer.schedule(new TimerTask() {
            public void run() {
                if (!server.isRunning()) {
                    LOGGER.error("API Server failed to start within {}ms.", startTimeout.toMillis());
                    SchedulerUtils.hardExit(SchedulerErrorCode.API_SERVER_TIMEOUT);
                }
            }
        }, startTimeout.toMillis());

        Runnable runServerCallback = new Runnable() {
            public void run() {
                try {
                    LOGGER.info("Starting API server");
                    server.start();
                    server.dumpStdErr();
                    LOGGER.info("API server started");
                    startTimer.cancel();
                    server.join();
                } catch (Exception e) {
                    LOGGER.error("API Server failed with exception: ", e);
                } finally {
                    LOGGER.info("API Server exiting.");
                    try {
                        server.destroy();
                    } catch (Exception e) {
                        LOGGER.error("Failed to stop API server with exception: ", e);
                    }
                }
            }
        };

        new Thread(runServerCallback).start();
    }
}
