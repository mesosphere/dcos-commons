package org.apache.mesos.scheduler.plan;

/**
 * Status of a {@link Element}.
 */
public enum Status {
  PENDING,
  IN_PROGRESS,
  COMPLETE,
  WAITING,
  ERROR
}
