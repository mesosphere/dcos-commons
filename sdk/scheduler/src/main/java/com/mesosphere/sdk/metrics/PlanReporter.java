package com.mesosphere.sdk.metrics;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mesosphere.sdk.scheduler.plan.PlanManager;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class encapsulates a simple reporter that scrapes plan statuses at a regular interval (5 seconds)
 * and outputs them to the metrics sink.
 */
public class PlanReporter {

    private static final int PLAN_SCRAPE_PERIOD_MS = 5000;

    private final Optional<String> namespace;
    private final Collection<PlanManager> managers;
    private final AtomicBoolean hasScraped;
    private final ScheduledExecutorService executor;

    public PlanReporter(Optional<String> namespace, Collection<PlanManager> managers) {
        this(namespace, managers, PLAN_SCRAPE_PERIOD_MS);
    }

    @VisibleForTesting
    PlanReporter(Optional<String> namespace, Collection<PlanManager> managers, int periodMs) {
        this.namespace = namespace;
        this.managers = managers;
        this.hasScraped = new AtomicBoolean(false);
        this.executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("PlanReporterThread")
                .build());

        executor.scheduleAtFixedRate(() -> {
            scrapeStatuses();
        }, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    private void scrapeStatuses() {
        for (PlanManager manager : managers) {
            Metrics.updatePlanStatus(namespace, manager.getPlan().getName(), manager.getPlan().getStatus());
        }
        hasScraped.compareAndSet(false, true);
    }

    @VisibleForTesting
    boolean getHasScraped() {
        return hasScraped.get();
    }
}
