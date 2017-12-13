package com.mesosphere.sdk.testing;

import java.util.Optional;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;

/**
 * A {@link Send} for sending a {@link Protos.TaskStatus} to a scheduler under test.
 */
public class SendTaskStatus implements Send {

    private final String taskName;
    private final Protos.TaskState taskState;
    private final Optional<Integer> readinessCheckExitCode;

    /**
     * Builder for {@link SendTaskStatus}.
     */
    public static class Builder {
        private final String taskName;
        private final Protos.TaskState taskState;
        private Optional<Integer> readinessCheckExitCode;

        Builder(String taskName, Protos.TaskState taskState) {
            this.taskName = taskName;
            this.taskState = taskState;
            this.readinessCheckExitCode = Optional.empty();
        }

        public Builder setReadinessCheckExitCode(int exitCode) {
            this.readinessCheckExitCode = Optional.of(exitCode);
            return this;
        }

        public Send build() {
            return new SendTaskStatus(taskName, taskState, readinessCheckExitCode);
        }
    }

    private SendTaskStatus(String taskName, Protos.TaskState taskState, Optional<Integer> readinessCheckExitCode) {
        this.taskName = taskName;
        this.taskState = taskState;
        this.readinessCheckExitCode = readinessCheckExitCode;
    }

    @Override
    public String getDescription() {
        return String.format("TaskStatus[state=%s,readinessExitCode=%s] for name=%s",
                taskState, readinessCheckExitCode, taskName);
    }

    @Override
    public void send(ClusterState state, SchedulerDriver mockDriver, Scheduler scheduler) {
        Protos.TaskStatus.Builder taskStatusBuilder = Protos.TaskStatus.newBuilder()
                .setTaskId(state.getTaskId(taskName))
                .setState(taskState)
                .setMessage("This is a test status");
        if (readinessCheckExitCode.isPresent()) {
            taskStatusBuilder.getCheckStatusBuilder().getCommandBuilder().setExitCode(readinessCheckExitCode.get());
        }
        scheduler.statusUpdate(mockDriver, taskStatusBuilder.build());
    }


}
