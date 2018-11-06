package com.mesosphere.sdk.state;

import com.google.common.util.concurrent.CycleDetectingLockFactory;
import com.mesosphere.sdk.framework.ProcessExit;
import com.mesosphere.sdk.scheduler.SchedulerConfig;

import java.util.concurrent.locks.ReadWriteLock;

/**
 * Common construction of thread locks that immediately log and kill the process if a deadlock is detected.
 */
public final class CycleDetectingLockUtils {

  /**
   * A custom deadlock policy which logs the error, and then exits the process.
   * After exiting, the process should then be restarted (by Marathon) in a fresh state.
   */
  private static final CycleDetectingLockFactory.Policy LOG_AND_EXIT_POLICY =
      e -> ProcessExit.exit(ProcessExit.DEADLOCK_ENCOUNTERED, e);

  private CycleDetectingLockUtils() {
    // do not instantiate
  }

  /**
   * Returns a new cycle detecting lock instance which has the provided label.
   *
   * @param schedulerConfig used to determine what should happen if a deadlock is detected
   * @param parentClass     to be used in any error messages
   */
  public static ReadWriteLock newLock(SchedulerConfig schedulerConfig, Class<?> parentClass) {
    return newLock(schedulerConfig.isDeadlockExitEnabled(), parentClass);
  }

  /**
   * Returns a new cycle detecting lock instance which has the provided label.
   *
   * @param exitOnDeadlock whether to exit the process if a deadlock is detected
   * @param parentClass    to be used in any error messages
   */
  public static ReadWriteLock newLock(boolean exitOnDeadlock, Class<?> parentClass) {
    return CycleDetectingLockFactory
        .newInstance(exitOnDeadlock ? LOG_AND_EXIT_POLICY : CycleDetectingLockFactory.Policies.WARN)
        .newReentrantReadWriteLock(parentClass.getSimpleName());
  }
}
