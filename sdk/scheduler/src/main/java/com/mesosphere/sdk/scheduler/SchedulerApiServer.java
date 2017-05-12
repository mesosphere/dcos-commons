package com.mesosphere.sdk.scheduler;

import com.google.common.base.Stopwatch;
import com.mesosphere.sdk.api.JettyApiServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * The SchedulerApiServer runs the {@link JettyApiServer} that exposes the Scheduler's API.
 */
public class SchedulerApiServer implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerApiServer.class);
    private Collection<Object> resources;
    private int port;
    private JettyApiServer apiServer;
    private Duration initTimeout;
    private Stopwatch apiServerStopwatch = Stopwatch.createStarted();

    /**
     * Constructs a SchedulerApiServer.
     *
     * @param port The port to listen on
     * @param resources The Collection of {@link Resource}s to expose as endpoints
     * @param initTimeout The initialization timeout, after which the Scheduler exits
     */
    public SchedulerApiServer(int port, Collection<Object> resources, Duration initTimeout) {
        this.port = port;
        this.resources = resources;
        this.initTimeout = initTimeout;
    }

    @Override
    public void run() {
        try {
            LOGGER.info("Starting API server.");
            apiServer = new JettyApiServer(port, resources);
            apiServer.start();
        } catch (Exception e) {
            LOGGER.error("API Server failed with exception: ", e);
        } finally {
            LOGGER.info("API Server exiting.");
            try {
                if (apiServer != null) {
                    apiServer.stop();
                }
            } catch (Exception e) {
                LOGGER.error("Failed to stop API server with exception: ", e);
            }
        }
    }

    public boolean ready() {
        boolean serverStarted = apiServer != null && apiServer.isStarted();

        if (serverStarted) {
            apiServerStopwatch.reset();
        } else {
            if (apiServerStopwatch.elapsed(TimeUnit.MILLISECONDS) > initTimeout.toMillis()) {
                LOGGER.error("API Server failed to start within {} seconds.", initTimeout.getSeconds());
                SchedulerUtils.hardExit(SchedulerErrorCode.API_SERVER_TIMEOUT);
            }
        }

        return serverStarted;
    }

}
