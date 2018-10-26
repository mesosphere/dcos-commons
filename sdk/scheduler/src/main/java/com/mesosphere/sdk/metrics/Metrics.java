package com.mesosphere.sdk.metrics;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.Status;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.readytalk.metrics.StatsDReporter;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import org.apache.mesos.Protos;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class encapsulates the components necessary for tracking Scheduler metrics.
 */
public final class Metrics {

  // Offers
  static final String RECEIVED_OFFERS = "offers.received";

  static final String PROCESSED_OFFERS = "offers.processed";

  static final String PROCESS_OFFERS = "offers.process";

  static final String REVIVES = "revives";

  static final String REVIVE_THROTTLES = "revives.throttles";

  static final String DECLINE_SHORT = "declines.short";

  static final String DECLINE_LONG = "declines.long";

  // Suppress
  private static final String SUPPRESSES = "suppresses";

  private static final String IS_SUPPRESSED = "is_suppressed";

  private static final MetricRegistry METRICS = new MetricRegistry();

  private static final AtomicBoolean isSuppressed = new AtomicBoolean(false);

  private Metrics() {}

  // This may be accessed both by whatever thread metrics runs on, and the main offer processing thread:

  static {
    METRICS.register(IS_SUPPRESSED, new Gauge<Boolean>() {
      @Override
      public Boolean getValue() {
        return isSuppressed.get();
      }
    });
  }

  public static MetricRegistry getRegistry() {
    return METRICS;
  }

  /**
   * Configures the metrics service to emit StatsD-formatted metrics to the configured UDP host/port with the
   * specified interval.
   */
  public static void configureStatsd(SchedulerConfig schedulerConfig) {
    StatsDReporter.forRegistry(METRICS)
        .build(schedulerConfig.getStatsdHost(), schedulerConfig.getStatsdPort())
        .start(schedulerConfig.getStatsDPollIntervalS(), TimeUnit.SECONDS);
  }

  /**
   * Appends endpoint servlets to the provided {@code context} which will serve codahale-style and prometheus-style
   * metrics.
   */
  public static void configureMetricsEndpoints(
      ServletContextHandler context, String codahaleMetricsEndpoint, String prometheusEndpoint)
  {
    // Metrics
    ServletHolder codahaleMetricsServlet = new ServletHolder("default",
        new com.codahale.metrics.servlets.MetricsServlet(METRICS));
    context.addServlet(codahaleMetricsServlet, codahaleMetricsEndpoint);

    // Prometheus
    CollectorRegistry collectorRegistry = new CollectorRegistry();
    collectorRegistry.register(new DropwizardExports(METRICS));
    ServletHolder prometheusServlet = new ServletHolder("prometheus",
        new io.prometheus.client.exporter.MetricsServlet(collectorRegistry));
    context.addServlet(prometheusServlet, prometheusEndpoint);
  }

  public static void incrementReceivedOffers(long amount) {
    METRICS.counter(RECEIVED_OFFERS).inc(amount);
  }

  // Revive

  public static void incrementProcessedOffers(long amount) {
    METRICS.counter(PROCESSED_OFFERS).inc(amount);
  }

  /**
   * Returns a timer context which may be used to measure the time spent processing offers. The returned timer must
   * be terminated by invoking {@link Timer.Context#stop()}.
   */
  public static Timer.Context getProcessOffersDurationTimer() {
    return METRICS.timer(PROCESS_OFFERS).time();
  }

  public static void notSuppressed() {
    Metrics.isSuppressed.set(false);
  }

  public static void incrementSuppresses() {
    METRICS.counter(SUPPRESSES).inc();
    Metrics.isSuppressed.set(true);
  }

  // Decline

  public static void incrementRevives() {
    METRICS.counter(REVIVES).inc();
  }

  public static void incrementReviveThrottles() {
    METRICS.counter(REVIVE_THROTTLES).inc();
  }

  public static void incrementDeclinesShort(long amount) {
    METRICS.counter(DECLINE_SHORT).inc(amount);
  }

  public static void incrementDeclinesLong(long amount) {
    METRICS.counter(DECLINE_LONG).inc(amount);
  }

  public static void incrementRecommendations(Collection<OfferRecommendation> recommendations) {
    for (OfferRecommendation recommendation : recommendations) {
      recommendation.getOperation().ifPresent(operation -> {
        // Metric name will be of the form "operation.launch"
        METRICS
            .counter(String.format("operation.%s", operation.getType().name().toLowerCase()))
            .inc();
      });
    }
  }

  /**
   * Records the provided {@code taskStatus} received from Mesos.
   */
  public static void record(Protos.TaskStatus taskStatus) {
    // Metric name will be of the form "task_status.running"
    METRICS
        .counter(String.format("task_status.%s", taskStatus.getState().name().toLowerCase()))
        .inc();
  }

  public static void updatePlanStatus(Optional<String> namespace, String planName, Status status) {
    final String metricName = namespace.isPresent()
        ? String.format("plan_status.%s.%s", namespace.get(), planName)
        : String.format("plan_status.%s", planName);

    // Returns the existing PlanGauge, or the provided new PlanGauge if none was configured yet:
    PlanGauge newOrExistingGauge = (PlanGauge) METRICS.gauge(metricName, PlanGauge::new);
    newOrExistingGauge.setStatus(status);
  }

  @VisibleForTesting
  static class PlanGauge implements Gauge<Integer> {
    private Status status;

    public PlanGauge setStatus(Status status) {
      this.status = status;
      return this;
    }

    @Override
    public Integer getValue() {
      // We return a set of user facing statuses rather than the complete set of statuses available.
      // This also insulates us against changes to the Status enum.
      //
      // The values step upwards when the plan is active.
      switch (status) {
        case ERROR:
          return -1;
        case COMPLETE:
          return 0;
        case WAITING:
        case PENDING:
          return 1;
        case IN_PROGRESS:
        case PREPARED:
        case STARTED:
        case STARTING:
          return 2;
        default:
          throw new IllegalStateException(String.format("Unsupported status: %s", status));
      }
    }
  }
}
