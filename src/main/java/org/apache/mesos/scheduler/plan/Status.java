package org.apache.mesos.scheduler.plan;

/**
 * Status of the block. Useful for reporting
 */
public enum Status {
  ERROR,
  WAITING,
  PENDING,
  IN_PROGRESS,
  COMPLETE
}
