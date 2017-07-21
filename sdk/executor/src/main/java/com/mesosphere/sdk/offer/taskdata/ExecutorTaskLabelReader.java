package com.mesosphere.sdk.offer.taskdata;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.mesos.Protos.HealthCheck;
import org.apache.mesos.Protos.TaskInfo;

import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.specification.GoalState;

/**
 * Provides read access to task labels which are (only) read by the Executor.
 */
public class ExecutorTaskLabelReader {

    private static final Set<String> VALID_GOAL_STATES = Arrays.stream(GoalState.values())
            .map(goalState -> goalState.name())
            .collect(Collectors.toSet());

    private final LabelReader reader;

    public ExecutorTaskLabelReader(TaskInfo taskInfo) {
        reader = new LabelReader(String.format("Task %s", taskInfo.getName()), taskInfo.getLabels());
    }

    /**
     * Returns the Task's {@link GoalState}, e.g. RUNNING or FINISHED.
     */
    public GoalState getGoalState() throws TaskException {
        String goalStateString = reader.getOrThrow(LabelConstants.GOAL_STATE_LABEL);
        if (!VALID_GOAL_STATES.contains(goalStateString)) {
            throw new TaskException("Unexpected goal state encountered: " + goalStateString);
        }

        return GoalState.valueOf(goalStateString);
    }

    /**
     * Returns the readiness check to be run by the Executor on task startup.
     */
    public Optional<HealthCheck> getReadinessCheck() throws TaskException {
        Optional<String> readinessCheckStrOptional = reader.getOptional(LabelConstants.READINESS_CHECK_LABEL);
        if (!readinessCheckStrOptional.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(LabelUtils.decodeHealthCheck(readinessCheckStrOptional.get()));
    }
}
