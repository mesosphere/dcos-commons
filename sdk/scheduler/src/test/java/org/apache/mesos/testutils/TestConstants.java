package org.apache.mesos.testutils;

import org.apache.mesos.Protos;
import org.apache.mesos.executor.ExecutorUtils;
import org.apache.mesos.offer.TaskUtils;

/**
 * This class encapsulates constants for tests.
 */
public class TestConstants {
    public static final String SERVICE_NAME = "service-name";
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
    public static final String TASK_TYPE = "test-task-type";
    public static final Integer TASK_INDEX = 0;
    public static final String TASK_CMD = "./task-cmd";
    public static final String POD_TYPE = "pod-type";
    public static final String HEALTH_CHECK_CMD = "./health-check";
    public static final String RESOURCE_ID = "test-resource-id";
    public static final String RESOURCE_SET_ID = "test-resource-set-id";
    public static final Protos.ExecutorID EXECUTOR_ID = ExecutorUtils.toExecutorId(EXECUTOR_NAME);
    public static final Protos.TaskID TASK_ID = TaskUtils.toTaskId(TASK_NAME);
    public static final String PORT_NAME = "test-port-name";
    public static final String VIP_KEY = "VIP_TEST";
    public static final String VIP_NAME = "testvip";

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

    public static final Protos.ContainerInfo CONTAINER_INFO =
            Protos.ContainerInfo.newBuilder()
            .setType(Protos.ContainerInfo.Type.DOCKER)
            .setDocker(
                    Protos.ContainerInfo.DockerInfo.newBuilder()
                    .setImage("bash")
                    .setNetwork(Protos.ContainerInfo.DockerInfo.Network.HOST)
                    .build()
            )
            .build();

    public static final Protos.CommandInfo COMMAND_INFO =
            Protos.CommandInfo.newBuilder()
            .setValue("echo test")
            .build();
}
