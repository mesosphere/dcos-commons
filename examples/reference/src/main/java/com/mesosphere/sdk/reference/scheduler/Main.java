package com.mesosphere.sdk.reference.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.offer.ValueUtils;
import org.apache.mesos.offer.constrain.TaskTypeGenerator;
import org.apache.mesos.scheduler.SchedulerUtils;
import org.apache.mesos.specification.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Main entry point for the Reference Scheduler.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final String SERVICE_NAME = "data-store";
    private static final String ROLE = SchedulerUtils.nameToRole(SERVICE_NAME);
    private static final String PRINCIPAL = SchedulerUtils.nameToPrincipal(SERVICE_NAME);

    private static final int POD_COUNT = Integer.parseInt(System.getenv("POD_COUNT"));
    private static final String TASK_METADATA_NAME = "meta-data";
    private static final int TASK_METADATA_COUNT = Integer.parseInt(System.getenv("METADATA_COUNT"));
    private static final double TASK_METADATA_CPU = Double.valueOf(System.getenv("METADATA_CPU"));
    private static final double TASK_METADATA_MEM_MB = Double.valueOf(System.getenv("METADATA_MEM"));
    private static final double TASK_METADATA_DISK_MB = Double.valueOf(System.getenv("METADATA_DISK"));

    private static final double SLEEP_DURATION = Double.valueOf(System.getenv("SLEEP_DURATION"));

    private static final int API_PORT = Integer.parseInt(System.getenv("PORT0"));
    private static final String CONTAINER_PATH_SUFFIX = "-container-path";

    public static void main(String[] args) throws Exception {
        LOGGER.info("Starting reference scheduler with args: " + Arrays.asList(args));
        new DefaultService(API_PORT).register(getServiceSpecification());
    }

    private static ServiceSpecification getServiceSpecification() {

        List<TaskSpecification> podTasks = Arrays.asList(
                new DefaultTaskSpecification(
                        TASK_METADATA_NAME,
                        getCommand(TASK_METADATA_NAME),
                        getResources(TASK_METADATA_CPU, TASK_METADATA_MEM_MB),
                        getVolumes(TASK_METADATA_DISK_MB, TASK_METADATA_NAME),
                        Optional.of(getHealthCheck(TASK_METADATA_NAME))),
                new DefaultTaskSpecification(
                        "my-other-task",
                        getCommand("my-other-task"),
                        getResources(TASK_METADATA_CPU, TASK_METADATA_MEM_MB),
                        getVolumes(TASK_METADATA_DISK_MB, "my-other-task"),
                        Optional.of(getHealthCheck("my-other-task")))
        );

        // Define only one set of pods
        PodSetSpecification podSet = DefaultPodSetSpecification.create(
                        POD_COUNT, // # of pods
                        "test-pod",// pod name
                        podTasks);

        return new DefaultServiceSpecification(SERVICE_NAME, Arrays.asList(podSet));
    }

    private static Protos.HealthCheck getHealthCheck(String name) {
        Protos.CommandInfo commandInfo = Protos.CommandInfo.newBuilder()
                .setValue(String.format("stat %s%s/output", name, CONTAINER_PATH_SUFFIX))
                .build();

        return Protos.HealthCheck.newBuilder()
                .setCommand(commandInfo)
                .build();
    }

    private static Collection<ResourceSpecification> getResources(double cpu, double memMb) {
        return Arrays.asList(
                new DefaultResourceSpecification(
                        "cpus",
                        ValueUtils.getValue(ResourceUtils.getUnreservedScalar("cpus", cpu)),
                        ROLE,
                        PRINCIPAL),
                new DefaultResourceSpecification(
                        "mem",
                        ValueUtils.getValue(ResourceUtils.getUnreservedScalar("mem", memMb)),
                        ROLE,
                        PRINCIPAL));
    }

    private static Collection<VolumeSpecification> getVolumes(double diskMb, String taskName) {
        VolumeSpecification volumeSpecification = new DefaultVolumeSpecification(
                diskMb,
                VolumeSpecification.Type.ROOT,
                taskName + CONTAINER_PATH_SUFFIX,
                ROLE,
                PRINCIPAL);

        return Arrays.asList(volumeSpecification);
    }

    private static Protos.CommandInfo getCommand(String name) {
        final String cmd = String.format(
                "echo %s >> %s%s/output && sleep %s", name, name, CONTAINER_PATH_SUFFIX, SLEEP_DURATION);

        return Protos.CommandInfo.newBuilder()
                .setValue(cmd)
                .build();
    }
}
