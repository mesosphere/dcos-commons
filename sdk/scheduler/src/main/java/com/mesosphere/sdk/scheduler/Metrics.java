package com.mesosphere.sdk.scheduler;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.OperationRecorder;
import com.readytalk.metrics.StatsDReporter;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;

import java.util.concurrent.TimeUnit;

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

    // Decline / Revive
    static final String REVIVES = "revives";
    static final String REVIVE_THROTTLES = "revives.throttles";
    static final String DECLINE_SHORT = "declines.short";
    static final String DECLINE_LONG = "declines.long";

    public static void incrementRevives() {
        metrics.counter(REVIVES).inc();
    }

    public static void incrementReviveThrottles() {
        metrics.counter(REVIVE_THROTTLES).inc();
    }

    public static void incrementDeclinesShort(long amount) {
        metrics.counter(DECLINE_SHORT).inc(amount);
    }

    public static void incrementDeclinesLong(long amount) {
        metrics.counter(DECLINE_LONG).inc(amount);
    }

    /**
     * This class records counter metrics for all Mesos Operations performed by the scheduler.
     */
    public static class OperationsCounter implements OperationRecorder {
        private static final OperationsCounter metricsRecorder = new OperationsCounter();
        private static final String PREFIX = "operation";

        private OperationsCounter() {
            // Do not instantiate this singleton class.
        }

        public static OperationsCounter getInstance() {
            return metricsRecorder;
        }

        @Override
        public void record(OfferRecommendation offerRecommendation) throws Exception {
            // Drop launch recommendations which are for bookkeeping purposes only
            if (offerRecommendation instanceof LaunchOfferRecommendation &&
                    !((LaunchOfferRecommendation) offerRecommendation).shouldLaunch()) {
                return;
            }

            // Metric name will be of the form "operation.launch"
            final String metricName = String.format(
                    "%s.%s",
                    PREFIX,
                    offerRecommendation.getOperation().getType().name().toLowerCase());

            metrics.counter(metricName).inc();
        }
    }

    /**
     * Records the provided {@code taskStatus} received from Mesos.
     */
    public static void record(Protos.TaskStatus taskStatus) {
        final String prefix = "task_status";

        // Metric name will be of the form "task_status.running"
        final String metricName = String.format(
                "%s.%s",
                prefix,
                taskStatus.getState().name().toLowerCase());

        metrics.counter(metricName).inc();
    }
}
