package org.apache.mesos.executor;

import org.apache.mesos.Protos;

/**
 * This class encapsulates some utility methods for generating test objects necessary for testing health check handling.
 */
public class HealthCheckTestUtils {
    private static final double SHORT_INTERVAL_S = 0.001;
    private static final double SHORT_DELAY_S = 0.001;
    private static final double SHORT_GRACE_PERIOD_S = 0.001;

    public static Protos.TaskInfo getFailingTask(int maxConsecutiveFailures) {
        return Protos.TaskInfo.newBuilder()
                .setName("task-failing-health-check")
                .setTaskId(Protos.TaskID.newBuilder().setValue("task-failing-health-check-task-id"))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue("task-failing-health-check-slave-id"))
                .setHealthCheck(getFailingHealthCheck(maxConsecutiveFailures))
                .build();
    }

    public static Protos.TaskInfo getSuccesfulTask() {
        return Protos.TaskInfo.newBuilder()
                .setName("task-passing-health-check")
                .setTaskId(Protos.TaskID.newBuilder().setValue("task-passing-health-check-task-id"))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue("task-passing-health-check-slave-id"))
                .setHealthCheck(getSuccessfulHealthCheck())
                .build();
    }

    public static Protos.HealthCheck getSuccessfulHealthCheck() {
        return getHealthCheck("echo success", 1);
    }

    public static Protos.HealthCheck getFailingHealthCheck(int maxConsecutiveFailures) {
        return getHealthCheck("this command should fail", maxConsecutiveFailures);
    }

    private static Protos.HealthCheck getHealthCheck(String cmd, int maxConsecutiveFailures) {
        return Protos.HealthCheck.newBuilder()
                .setIntervalSeconds(SHORT_INTERVAL_S)
                .setDelaySeconds(SHORT_DELAY_S)
                .setGracePeriodSeconds(SHORT_GRACE_PERIOD_S)
                .setConsecutiveFailures(maxConsecutiveFailures)
                .setCommand(Protos.CommandInfo.newBuilder()
                        .setValue(cmd)
                        .build())
                .build();
    }
}
