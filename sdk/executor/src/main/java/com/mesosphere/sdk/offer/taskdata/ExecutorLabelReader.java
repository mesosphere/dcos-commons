package com.mesosphere.sdk.offer.taskdata;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.mesos.Protos.TaskInfo;

import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.specification.GoalState;

/**
 * Provides read access to task labels which are (only) read by the Executor.
 */
public class ExecutorLabelReader extends LabelReader {

    private static final Set<String> VALID_GOAL_STATES = Arrays.stream(GoalState.values())
            .map(goalState -> goalState.name())
            .collect(Collectors.toSet());

    public ExecutorLabelReader(TaskInfo taskInfo) {
        super(taskInfo);
    }

    /**
     * Returns the Task's {@link GoalState}, e.g. RUNNING or FINISHED.
     */
    public GoalState getGoalState() throws TaskException {
        String goalStateString = getOrThrow(LabelConstants.GOAL_STATE_LABEL);
        if (!VALID_GOAL_STATES.contains(goalStateString)) {
            throw new TaskException("Unexpected goal state encountered: " + goalStateString);
        }

        return GoalState.valueOf(goalStateString);
    }
}
