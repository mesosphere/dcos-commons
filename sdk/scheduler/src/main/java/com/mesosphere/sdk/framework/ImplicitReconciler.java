package com.mesosphere.sdk.framework;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically triggers implicit reconciliation of all tasks being managed by the scheduler.
 * Mesos recommends that schedulers trigger an implicit reconciliation occasionally.
 * <p>
 * This differs from Explicit Reconciliation in two ways:
 * <ul>
 * <li>
 * Explicit Reconciliation is tied to a specific set of tasks, and is therefore service-specific
 * rather than framework-wide.
 * </li>
 * <li>
 * While Implicit Reconciliation is run periodically on a timer, Explicit Reconciliation is only
 * performed once on service startup.
 * </li>
 * </ul>
 *
 * @see <a href="http://mesos.apache.org/documentation/latest/reconciliation/">Reconciliation</a>
 */
class ImplicitReconciler {

  private static final Logger LOGGER = LoggingUtils.getLogger(ImplicitReconciler.class);

  private static final Runnable RECONCILE_CMD = () -> {
    try {
      LOGGER.info("Triggering implicit reconciliation");
      Driver.getInstance().reconcileTasks(Collections.emptyList());
    } catch (Exception e) { // SUPPRESS CHECKSTYLE IllegalCatch
      LOGGER.error("Failed to trigger implicit reconciliation", e);
    }
  };

  private final ScheduledExecutorService reconcileExecutor =
      Executors.newScheduledThreadPool(1);

  private final SchedulerConfig schedulerConfig;

  // Whether we should run in multithreaded mode. Should only be disabled for tests.
  private boolean multithreaded = true;

  private boolean started = false;

  public ImplicitReconciler(SchedulerConfig schedulerConfig) {
    this.schedulerConfig = schedulerConfig;
  }

  /**
   * Forces the instance to run in a synchronous/single-threaded mode for tests. To have any effect,
   * this must be called before calling {@link #start()}.
   *
   * @return this
   */
  ImplicitReconciler disableThreading() {
    multithreaded = false;
    return this;
  }

  /**
   * Starts the implicit reconciliation timer on a separate thread, or runs a single implicit
   * reconciliation on this thread if {@link #disableThreading()} was called.
   */
  public void start() {
    if (started) {
      throw new IllegalStateException("Start was already called");
    }
    started = true;

    if (multithreaded) {
      // Start the background thread which will periodically trigger implicit reconciliation
      // operations.
      reconcileExecutor.scheduleWithFixedDelay(
          RECONCILE_CMD,
          schedulerConfig.getImplicitReconcileDelayMs(),
          schedulerConfig.getImplicitReconcilePeriodMs(),
          TimeUnit.MILLISECONDS);
    } else {
      // In single-threaded mode, just run implicit reconciliation once then exit.
      RECONCILE_CMD.run();
    }
  }

  /**
   * Stops the implicit reconciliation timer. For testing.
   */
  @VisibleForTesting
  void stop() throws InterruptedException {
    reconcileExecutor.shutdown();
    reconcileExecutor.awaitTermination(5, TimeUnit.SECONDS); // SUPPRESS CHECKSTYLE MagicNumber
    started = false;
  }
}
