package com.mesosphere.sdk.testutils;

import org.apache.mesos.Protos;

import com.mesosphere.sdk.offer.CommonIdUtils;

/**
 * This class encapsulates constants for tests.
 */
public class TestConstants {
    public static final String SERVICE_NAME = "service-name";
    public static final String CONTAINER_PATH = "test-container-path";
    public static final String CONTAINER_PATH_LABEL = "container-path";
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
    public static final String TASK_DNS_PREFIX = "task-prefix";
    public static final Protos.ExecutorID EXECUTOR_ID = CommonIdUtils.toExecutorId(EXECUTOR_NAME);
    public static final Protos.TaskID TASK_ID = CommonIdUtils.toTaskId(TASK_NAME);
    public static final String PORT_ENV_NAME = "TEST_PORT_NAME";
    public static final String VIP_NAME = "testvip";
    public static final int VIP_PORT = 1111;
    public static final String VIP_PROTOCOL = "tcp";
    public static final Protos.DiscoveryInfo.Visibility VIP_VISIBILITY = Protos.DiscoveryInfo.Visibility.EXTERNAL;
    public static final Integer PORT_API_VALUE = 8080;
    public static final String HAS_DYNAMIC_PORT_ASSIGNMENT_LABEL = "has-dynamic-port-assignment";
    public static final String HAS_VIP_LABEL = "hasvip";
    public static final String MOUNT_SOURCE_ROOT = "/mnt/source";
    // CNI port mapping constants
    public static final int HOST_PORT = 4040;
    public static final int CONTAINER_PORT = 8080;
    public static final int NUMBER_OF_PORT_MAPPINGS = 1;

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

    public static final Protos.TaskInfo TASK_INFO =
            Protos.TaskInfo.newBuilder()
            .setName(TASK_NAME)
            .setTaskId(TASK_ID)
            .setSlaveId(AGENT_ID)
            .build();

    public static final Protos.TaskStatus TASK_STATUS =
            Protos.TaskStatus.newBuilder()
                    .setTaskId(TASK_ID)
                    .setState(Protos.TaskState.TASK_RUNNING)
                    .build();

    public static Protos.Labels getRequiredTaskLabels(int podIndex) {
        Protos.Labels.Builder builder = Protos.Labels.newBuilder();
        builder.addLabelsBuilder().setKey("task_type").setValue(TASK_TYPE);
        builder.addLabelsBuilder().setKey("index").setValue(String.valueOf(podIndex));
        return builder.build();
    }
}
