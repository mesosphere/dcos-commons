package org.apache.mesos.scheduler.plan;

import org.apache.mesos.specification.TaskSet;

/**
 * Created by gabriel on 10/15/16.
 */
public interface PhaseFactory {
    Phase getPhase(TaskSet taskSet);
}
