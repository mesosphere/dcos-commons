package org.apache.mesos.scheduler.plan;

/**
 * Used by Plans, Phases and Blocks to indicate they are complete.
 */
public interface Completable {

  /**
   * Indicates whether a completed is complete.
   *
   * @return
   */
  boolean isComplete();

}
