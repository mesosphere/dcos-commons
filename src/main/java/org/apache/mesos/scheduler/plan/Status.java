package org.apache.mesos.scheduler.plan;

/**
 * Status of the block. Useful for reporting
 */
public enum Status {
  Error,
  Waiting,
  Pending,
  InProgress,
  Complete
}
