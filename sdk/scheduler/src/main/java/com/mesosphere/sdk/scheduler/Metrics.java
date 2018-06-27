package com.mesosphere.sdk.scheduler;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.readytalk.metrics.StatsDReporter;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.mesos.Protos;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * This class encapsulates the components necessary for tracking Scheduler metrics.
 */
public class Metrics {
    private static MetricRegistry metrics = new MetricRegistry();

    public static MetricRegistry getRegistry() {
        return metrics;
    }

    /**
     * Configures the metrics service to emit StatsD-formatted metrics to the configured UDP host/port with the
     * specified interval.
     */
    public static void configureStatsd(SchedulerConfig schedulerConfig) {
        StatsDReporter.forRegistry(metrics)
                .build(schedulerConfig.getStatsdHost(), schedulerConfig.getStatsdPort())
                .start(schedulerConfig.getStatsDPollIntervalS(), TimeUnit.SECONDS);
    }

    /**
     * Appends endpoint servlets to the provided {@code context} which will serve codahale-style and prometheus-style
     * metrics.
     */
    public static void configureMetricsEndpoints(
            ServletContextHandler context, String codahaleMetricsEndpoint, String prometheusEndpoint) {
        // Metrics
        ServletHolder codahaleMetricsServlet = new ServletHolder("default",
                new com.codahale.metrics.servlets.MetricsServlet(metrics));
        context.addServlet(codahaleMetricsServlet, codahaleMetricsEndpoint);

        // Prometheus
        CollectorRegistry collectorRegistry = new CollectorRegistry();
        collectorRegistry.register(new DropwizardExports(metrics));
        ServletHolder prometheusServlet = new ServletHolder("prometheus",
                new io.prometheus.client.exporter.MetricsServlet(collectorRegistry));
        context.addServlet(prometheusServlet, prometheusEndpoint);
    }

    // Offers
    static final String RECEIVED_OFFERS = "offers.received";
    static final String PROCESSED_OFFERS = "offers.processed";
    static final String PROCESS_OFFERS = "offers.process";

    public static void incrementReceivedOffers(long amount) {
        metrics.counter(RECEIVED_OFFERS).inc(amount);
    }

    public static void incrementProcessedOffers(long amount) {
        metrics.counter(PROCESSED_OFFERS).inc(amount);
    }

    /**
     * Returns a timer context which may be used to measure the time spent processing offers. The returned timer must
     * be terminated by invoking {@link Timer.Context#stop()}.
     */
    public static Timer.Context getProcessOffersDurationTimer() {
        return metrics.timer(PROCESS_OFFERS).time();
    }

    // Suppress

    static final String SUPPRESSES = "suppresses";
    static final String IS_SUPPRESSED = "is_suppressed";

    private static AtomicBoolean isSuppressed = new AtomicBoolean(false);
    static {
        metrics.register(IS_SUPPRESSED, new Gauge<Boolean>() {
            @Override
            public Boolean getValue() {
                return isSuppressed.get();
            }
        });
    }

    public static void notSuppressed() {
        Metrics.isSuppressed.set(false);
    }

    public static void incrementSuppresses() {
        metrics.counter(SUPPRESSES).inc();
        Metrics.isSuppressed.set(true);
    }

    // Revive

    static final String REVIVES = "revives";
    static final String REVIVE_THROTTLES = "revives.throttles";

    public static void incrementRevives() {
        metrics.counter(REVIVES).inc();
    }

    public static void incrementReviveThrottles() {
        metrics.counter(REVIVE_THROTTLES).inc();
    }

    // Decline

    static final String DECLINE_SHORT = "declines.short";
    static final String DECLINE_LONG = "declines.long";

    public static void incrementDeclinesShort(long amount) {
        metrics.counter(DECLINE_SHORT).inc(amount);
    }

    public static void incrementDeclinesLong(long amount) {
        metrics.counter(DECLINE_LONG).inc(amount);
    }

    public static void incrementRecommendations(Collection<OfferRecommendation> recommendations) {
        for (OfferRecommendation recommendation : recommendations) {
            // Metric name will be of the form "operation.launch"
            final String metricName =
                    String.format("operation.%s", recommendation.getOperation().getType().name().toLowerCase());
            metrics.counter(metricName).inc();
        }
    }

    /**
     * Records the provided {@code taskStatus} received from Mesos.
     */
    public static void record(Protos.TaskStatus taskStatus) {
        // Metric name will be of the form "task_status.running"
        final String metricName = String.format("task_status.%s", taskStatus.getState().name().toLowerCase());
        metrics.counter(metricName).inc();
    }
}
