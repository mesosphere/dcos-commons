package org.apache.mesos.testutils;

import org.apache.mesos.Protos;
import org.apache.mesos.executor.ExecutorUtils;
import org.apache.mesos.offer.TaskUtils;

/**
 * This class encapsulates constants for tests.
 */
public class TestConstants {
    public static final String containerPath = "test-container-path";
    public static final String executorName = "test-executor-name";
    public static final Protos.FrameworkID frameworkId =
            Protos.FrameworkID.newBuilder().setValue("test-framework-id").build();
    public static final String hostname = "test-hostname";
    public static final String mountRoot = "test-mount-root";
    public static final Protos.OfferID offerId = Protos.OfferID.newBuilder().setValue("test-offer-id").build();
    public static final String persistenceId = "test-persistence-id";
    public static final String principal = "test-principal";
    public static final String role = "test-role";
    public static final Protos.SlaveID agentId = Protos.SlaveID.newBuilder().setValue("test-slave-id").build();
    public static final String taskName = "test-task-name";
    public static final String resourceId = "test-resource-id";
    public static final Protos.ExecutorID executorId = ExecutorUtils.toExecutorId(executorName);
    public static final Protos.TaskID taskId = TaskUtils.toTaskId(taskName);
    public static final Protos.MasterInfo masterInfo =
            Protos.MasterInfo.newBuilder()
                    .setId("test-master-id")
                    .setIp(0)
                    .setPort(0)
                    .build();
}
