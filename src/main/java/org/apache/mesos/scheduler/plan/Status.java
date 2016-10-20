package org.apache.mesos.scheduler.plan;

/**
 * Status of an {@link Element}.
 */
public enum Status {
  ERROR,
  WAITING,
  PENDING,
  IN_PROGRESS,
  COMPLETE
}
