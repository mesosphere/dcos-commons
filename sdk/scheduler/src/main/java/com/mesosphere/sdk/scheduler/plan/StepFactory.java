package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.TaskSpec;

import java.util.Collection;

/**
 * An implementation of this interface should provide {@link Step}s based on {@link TaskSpec}s.  This should
 * include logic detecting that a previously launched Task's specification has changed and so a relaunch of the Task is
 * needed.
 */
public interface StepFactory {
    Step getStep(PodInstance podInstance, Collection<String> tasksToLaunch);
}
