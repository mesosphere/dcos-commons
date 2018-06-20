package com.mesosphere.sdk.metrics;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanManager;

import java.util.List;
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

    private final List<PlanManager> managers;
    private final ScheduledExecutorService executor;
    private final Optional<String> namespace;
    @VisibleForTesting
    final AtomicBoolean hasScraped;

    public PlanReporter(Optional<String> namespace, List<PlanManager> managers) {
        this.managers = managers;
        this.namespace = namespace;
        this.executor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat("PlanReporterThread")
                        .build()
        );

        this.hasScraped = new AtomicBoolean(false);
        executor.scheduleAtFixedRate(() -> {
            scrapeStatuses(managers);
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void scrapeStatuses(List<PlanManager> managers) {
        for (PlanManager manager : managers) {
            Plan plan = manager.getPlan();
            if (plan != null) {
                Metrics.setPlanStatus(namespace, plan.getName(), plan.getStatus());
            }
        }

        hasScraped.compareAndSet(false, true);
    }
}
