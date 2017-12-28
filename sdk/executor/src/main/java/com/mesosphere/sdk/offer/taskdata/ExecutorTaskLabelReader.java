package com.mesosphere.sdk.offer.taskdata;

import java.util.Optional;

import org.apache.mesos.Protos.HealthCheck;
import org.apache.mesos.Protos.TaskInfo;

import com.mesosphere.sdk.offer.TaskException;

/**
 * Provides read access to task labels which are (only) read by the Executor.
 */
public class ExecutorTaskLabelReader {

    private final LabelReader reader;

    public ExecutorTaskLabelReader(TaskInfo taskInfo) {
        reader = new LabelReader(String.format("Task %s", taskInfo.getName()), taskInfo.getLabels());
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
