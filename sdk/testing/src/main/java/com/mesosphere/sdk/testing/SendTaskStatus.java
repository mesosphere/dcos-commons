package com.mesosphere.sdk.testing;

import java.util.Optional;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;

/**
 * A {@link Send} for sending a {@link Protos.TaskStatus} to a scheduler under test.
 */
public class SendTaskStatus implements Send {

    private final Builder builder;

    /**
     * Builder for {@link SendTaskStatus}.
     */
    public static class Builder {
        private final String taskName;
        private final Protos.TaskState taskState;
        private Optional<Integer> readinessCheckExitCode;
        private String taskId;

        Builder(String taskName, Protos.TaskState taskState) {
            this.taskName = taskName;
            this.taskState = taskState;
            this.readinessCheckExitCode = Optional.empty();
        }

        public Builder setReadinessCheckExitCode(int exitCode) {
            this.readinessCheckExitCode = Optional.of(exitCode);
            return this;
        }

        public Builder setTaskId(String id) {
            this.taskId = id;
            return this;
        }

        public Send build() {
            return new SendTaskStatus(this);
        }
    }

    private SendTaskStatus(Builder builder) {
        this.builder = builder;
    }

    @Override
    public String getDescription() {
        return String.format("TaskStatus[state=%s,readinessExitCode=%s] for name=%s and id=%s",
                builder.taskState, builder.readinessCheckExitCode, builder.taskName, builder.taskId);
    }

    @Override
    public void send(ClusterState state, SchedulerDriver mockDriver, Scheduler scheduler) {
        Protos.TaskID taskId = builder.taskId == null ?
                state.getTaskId(builder.taskName) :
                Protos.TaskID.newBuilder().setValue(builder.taskId).build();
        Protos.TaskStatus.Builder taskStatusBuilder = Protos.TaskStatus.newBuilder()
                .setTaskId(taskId)
                .setState(builder.taskState)
                .setMessage("This is a test status");

        if (builder.readinessCheckExitCode.isPresent()) {
            taskStatusBuilder
                    .getCheckStatusBuilder()
                    .getCommandBuilder()
                    .setExitCode(builder.readinessCheckExitCode.get());
        }
        scheduler.statusUpdate(mockDriver, taskStatusBuilder.build());
    }


}
