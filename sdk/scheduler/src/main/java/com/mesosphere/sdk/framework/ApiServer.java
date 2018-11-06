package com.mesosphere.sdk.framework;

import com.codahale.metrics.jetty9.InstrumentedHandler;
import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.metrics.Metrics;
import com.mesosphere.sdk.offer.LoggingUtils;
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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The {@link ApiServer} runs the Jetty {@link Server} that exposes the Scheduler's API.
 */
public class ApiServer {

  private static final Logger LOGGER = LoggingUtils.getLogger(ApiServer.class);

  private final int port;

  private final Server server;

  private final Duration startTimeout;

  private Thread serverThread;

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
    Metrics.configureMetricsEndpoints(
        context,
        "/v1/metrics",
        "/v1/metrics/prometheus");
    // Serve resources at their declared paths relative to root:
    context
        .addServlet(
            new ServletHolder(
                new ServletContainer(
                    new ResourceConfig(MultiPartFeature.class)
                        .registerInstances(new HashSet<>(resources))
                )
            ),
            "/*");

    // Passthru handler: Collect basic metrics on queries, and store those metrics in the registry
    // TODO(nickbp): reimplement InstrumentedHandler with better/more granular metrics
    // (e.g. resource being queried)
    final InstrumentedHandler instrumentedHandler = new InstrumentedHandler(Metrics.getRegistry());
    instrumentedHandler.setHandler(context);

    server.setHandler(instrumentedHandler);
  }

  private static void awaitSchedulerDns(String schedulerHostname, SchedulerConfig schedulerConfig) {
    final String expectedAddress = schedulerConfig.getSchedulerIP();
    final Duration waitTime = Duration.ofSeconds(5);

    LOGGER.info(
        "Waiting for the scheduler host {} to resolve to {}. Wait will timeout after {}ms",
        schedulerHostname,
        expectedAddress,
        schedulerConfig.getApiServerInitTimeout().toMillis());

    final Timer resolutionTimer = new Timer("API-resolution-timeout");
    resolutionTimer.schedule(new TimerTask() {
      @Override
      public void run() {
        LOGGER.error("Unable to lookup scheduler host {} within {}ms",
            schedulerHostname, schedulerConfig.getApiServerInitTimeout().toMillis());
        ProcessExit.exit(ProcessExit.API_SERVER_ERROR);
      }
    }, schedulerConfig.getApiServerInitTimeout().toMillis());

    while (true) {
      try {
        if (resolveSchedulerDNS(schedulerHostname, expectedAddress)) {
          break;
        }

        LOGGER.info("Waiting {}ms to retry scheduler host resolution", waitTime.toMillis());
        Thread.sleep(waitTime.toMillis());
      } catch (InterruptedException ie) {
        LOGGER.error("DNS resolution interrupted", ie);
        ProcessExit.exit(ProcessExit.API_SERVER_ERROR);
      } finally {
        resolutionTimer.cancel();
      }
    }
  }

  @VisibleForTesting
  static boolean resolveSchedulerDNS(String schedulerHostname, String expectedAddress) {
    try {
      final List<InetAddress> addresses = Stream.of(InetAddress.getAllByName(schedulerHostname))
          .collect(Collectors.toList());

      // It's acceptable to have both an ipv6 and an ipv4 address.
      // If there are two of each, uh oh.
      if (addresses.size() > 2) {
        LOGGER.warn("Scheduler host {} resolved to more than two addresses: {}", addresses);
        return false;
      } else if (addresses.size() == 2) {
        final boolean firstIsIpv4 = addresses.get(0) instanceof Inet4Address;
        final boolean secondIsIpv4 = addresses.get(1) instanceof Inet4Address;

        if ((firstIsIpv4 && secondIsIpv4) || (!firstIsIpv4 && !secondIsIpv4)) {
          LOGGER.warn("Scheduler host {} resolved to {} which are both {} addresses",
              schedulerHostname, addresses, firstIsIpv4 ? "IPV4" : "IPV6");
          return false;
        }
      }

      if (addresses
          .stream()
          .map(InetAddress::getHostAddress)
          .noneMatch(s -> s.equals(expectedAddress)))
      {
        LOGGER.warn(
            "Scheduler host {} resolved to {} not the expected address {}",
            schedulerHostname,
            addresses,
            expectedAddress);
        return false;
      }

      LOGGER.info("Resolved scheduler host {} to expected address {}",
          schedulerHostname, expectedAddress);
      return true;
    } catch (UnknownHostException uhe) {
      LOGGER.warn("Unable to look up scheduler host {}", schedulerHostname);
      return false;
    }
  }

  public static ApiServer start(
      String schedulerHostname,
      SchedulerConfig schedulerConfig,
      Collection<Object> resources,
      Runnable startedCallback)
  {
    ApiServer apiServer = new ApiServer(schedulerConfig, resources);
    apiServer.start(new AbstractLifeCycle.AbstractLifeCycleListener() {
      @Override
      public void lifeCycleStarted(LifeCycle event) {
        awaitSchedulerDns(schedulerHostname, schedulerConfig);
        startedCallback.run();
      }
    });
    return apiServer;
  }

  /**
   * Launches the API server on a separate thread.
   *
   * @param listener A listener object which will be notified when the underlying server changes
   *                 state
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
          LOGGER.error(
              "API Server failed to start at port {} within {}ms",
              port,
              startTimeout.toMillis());
          ProcessExit.exit(ProcessExit.API_SERVER_ERROR);
        }
      }
    }, startTimeout.toMillis());

    Runnable runServerCallback = () -> {
      try {
        LOGGER.info("Starting API server at port {}", port);
        server.start();
        int localPort = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
        LOGGER.info("API server started at port {}", localPort);
        startTimer.cancel();
        server.join();
      } catch (Exception e) { // SUPPRESS CHECKSTYLE IllegalCatch
        LOGGER.error(String.format("API server at port %d failed with exception: ", port), e);
        ProcessExit.exit(ProcessExit.API_SERVER_ERROR, e);
      } finally {
        LOGGER.info("API server at port {} exiting", port);
        try {
          server.destroy();
        } catch (Exception e) { // SUPPRESS CHECKSTYLE IllegalCatch
          LOGGER.error(
              String.format("Failed to stop API server at port %d with exception: ", port),
              e);
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
