package org.apache.mesos.reconciliation;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Task DefaultReconciler for Schedulers.
 */
@Singleton
public class DefaultReconciler implements Reconciler {
    private static final Logger LOGGER = LoggerFactory.getLogger
            (Reconciler.class);

    private static final int MULTIPLIER = 2;
    private static final long BASE_BACKOFF_MS = 4000;
    private static final long MAX_BACKOFF_MS = 30000;

    private final AtomicBoolean reconciled = new AtomicBoolean(false);
    private final Map<String, TaskStatus> unreconciled = new HashMap<>();
    private long lastRequestTime = 0;
    private long backOff = BASE_BACKOFF_MS;


    public DefaultReconciler() {

    }

    @Override
    public void start(final Collection<Protos.TaskStatus> tasks) {

        LOGGER.info("Starting Reconciler");

        synchronized (unreconciled) {
            for (TaskStatus status : tasks) {
                unreconciled.put(status.getTaskId().getValue(), status);
            }
            reconciled.set(unreconciled.isEmpty());
        }
    }

    @Override
    public void reconcile(final SchedulerDriver driver) {

        if (!reconciled.get()) {
            synchronized (unreconciled) {
                final long now = System.currentTimeMillis();
                if (unreconciled.isEmpty()) {
                    LOGGER.info("Reconciled all known tasks performing " +
                            "implicit reconciliation");
                    driver.reconcileTasks(Collections.<TaskStatus>emptyList());
                    lastRequestTime = 0;
                    backOff = BASE_BACKOFF_MS;
                    reconciled.set(true);
                } else if (now > lastRequestTime + backOff) {
                    LOGGER.info("Requesting reconciliation : time = {}", now);
                    lastRequestTime = now;
                    long newBackoff = backOff * MULTIPLIER;
                    backOff = Math.min(newBackoff > 0 ? newBackoff : 0,
                            MAX_BACKOFF_MS);
                    driver.reconcileTasks(unreconciled.values());
                    LOGGER.info("Next reconciliation = {}", now + backOff);
                } else {
                    LOGGER.info("Unreconciled : remaining tasks = {}, " +
                                    "next reconcile time = {}",
                            unreconciled.size(),
                            lastRequestTime + backOff);

                    LOGGER.info("Uncreconciled tasks = {}", unreconciled);
                }
            }
        }
    }

    @Override
    public void update(final Protos.TaskStatus status) {

        synchronized (unreconciled) {
            LOGGER.info("Reconciled task id = {}",
                    status.getTaskId().getValue());
            unreconciled.remove(status.getTaskId().getValue());
        }
    }

    @Override
    public Set<String> remaining() {
        synchronized (unreconciled) {
            return ImmutableSet.copyOf(unreconciled.keySet());
        }
    }

    @Override
    public void forceComplete() {
        synchronized (unreconciled) {
            unreconciled.clear();
        }
        reconciled.set(true);
    }

    @Override
    public boolean isReconciled() {
        return reconciled.get();
    }
}
