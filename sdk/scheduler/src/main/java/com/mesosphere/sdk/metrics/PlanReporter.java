package com.mesosphere.sdk.metrics;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class encapsulates a simple reporter that scrapes plan statuses at a regular interval and outputs them
 * to the metrics sink.
 *
 * This class is _NOT_ thread safe.
 */
public class PlanReporter {

    private final List<PlanManager> managers;
    private final ScheduledExecutorService executor;
    private final Optional<String> namespace;
    private boolean started = false;

    public PlanReporter(Optional<String> namespace, List<PlanManager> managers) {
        this.managers = managers;
        this.namespace = namespace;
        this.executor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat("PlanReporterThread")
                        .build()
        );
    }

    public void start() {
        if (started) {
            throw new IllegalArgumentException("Reporter already started");
        }

        executor.scheduleAtFixedRate(() -> {
            scrapeStatuses(managers);
        }, 0, 5, TimeUnit.SECONDS);

        started = true;
    }

    private void scrapeStatuses(List<PlanManager> managers) {
        for (PlanManager manager : managers) {
            Plan plan = manager.getPlan();
            if (plan != null) {
                Metrics.setPlanStatus(namespace, plan.getName(), plan.getStatus());
            }
        }
    }
}
