package org.apache.mesos.testutils;

import org.apache.mesos.Protos;
import org.apache.mesos.executor.ExecutorUtils;
import org.apache.mesos.offer.TaskUtils;

/**
 * This class encapsulates constants for tests.
 */
public class TestConstants {
    public static final String CONTAINER_PATH = "test-container-path";
    public static final String EXECUTOR_NAME = "test-executor-name";
    public static final String HOSTNAME = "test-hostname";
    public static final String MOUNT_ROOT = "test-mount-root";
    public static final Protos.OfferID OFFER_ID = Protos.OfferID.newBuilder().setValue("test-offer-id").build();
    public static final String PERSISTENCE_ID = "test-persistence-id";
    public static final String PRINCIPAL = "test-principal";
    public static final String ROLE = "test-role";
    public static final Protos.SlaveID AGENT_ID = Protos.SlaveID.newBuilder().setValue("test-slave-id").build();
    public static final String TASK_NAME = "test-task-name";
    public static final String RESOURCE_ID = "test-resource-id";
    public static final Protos.ExecutorID EXECUTOR_ID = ExecutorUtils.toExecutorId(EXECUTOR_NAME);
    public static final Protos.TaskID TASK_ID = TaskUtils.toTaskId(TASK_NAME);
    public static final String PORT_NAME = "test-port-name";

    public static final Protos.MasterInfo MASTER_INFO =
            Protos.MasterInfo.newBuilder()
                    .setId("test-master-id")
                    .setIp(0)
                    .setPort(0)
                    .build();

    public static final Protos.FrameworkID FRAMEWORK_ID =
            Protos.FrameworkID.newBuilder()
                    .setValue("test-framework-id")
                    .build();
}
