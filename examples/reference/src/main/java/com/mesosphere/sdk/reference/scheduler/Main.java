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

    private static final String TASK_METADATA_NAME = "meta-data";
    private static final int TASK_METADATA_COUNT = Integer.parseInt(System.getenv("METADATA_COUNT"));
    private static final double TASK_METADATA_CPU = Double.valueOf(System.getenv("METADATA_CPU"));
    private static final double TASK_METADATA_MEM_MB = Double.valueOf(System.getenv("METADATA_MEM"));
    private static final double TASK_METADATA_DISK_MB = Double.valueOf(System.getenv("METADATA_DISK"));

    private static final String TASK_DATA_NAME = "data";
    private static final int TASK_DATA_COUNT = Integer.parseInt(System.getenv("DATA_COUNT"));
    private static final double TASK_DATA_CPU = Double.valueOf(System.getenv("DATA_CPU"));
    private static final double TASK_DATA_MEM_MB = Double.valueOf(System.getenv("DATA_MEM"));
    private static final double TASK_DATA_DISK_MB = Double.valueOf(System.getenv("DATA_DISK"));

    private static final int API_PORT = Integer.parseInt(System.getenv("PORT0"));
    private static final String CONTAINER_PATH_SUFFIX = "-container-path";

    public static void main(String[] args) throws Exception {
        LOGGER.info("Starting reference scheduler with args: " + Arrays.asList(args));
        new DefaultService(API_PORT).register(getServiceSpecification());
    }

    private static ServiceSpecification getServiceSpecification() {
        return new DefaultServiceSpecification(
                SERVICE_NAME,
                Arrays.asList(
                        DefaultTaskSet.create(TASK_METADATA_COUNT,
                                TASK_METADATA_NAME,
                                getCommand(TASK_METADATA_NAME),
                                getResources(TASK_METADATA_CPU, TASK_METADATA_MEM_MB),
                                getVolumes(TASK_METADATA_DISK_MB, TASK_METADATA_NAME),
                                // no config info/template files
                                Collections.emptyList(),
                                // avoid colocating with other metadata instances (<=1 instance/agent):
                                Optional.of(TaskTypeGenerator.createAvoid(TASK_METADATA_NAME)),
                                Optional.of(getHealthCheck(TASK_METADATA_NAME))),
                        DefaultTaskSet.create(TASK_DATA_COUNT,
                                TASK_DATA_NAME,
                                getCommand(TASK_DATA_NAME),
                                getResources(TASK_DATA_CPU, TASK_DATA_MEM_MB),
                                getVolumes(TASK_DATA_DISK_MB, TASK_DATA_NAME),
                                // no config info/template files
                                Collections.emptyList(),
                                // avoid colocating with other data instances (<=1 instance/agent):
                                Optional.of(TaskTypeGenerator.createAvoid(TASK_DATA_NAME)),
                                Optional.of(getHealthCheck(TASK_DATA_NAME)))));
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
                "echo %s >> %s%s/output && sleep 1000", name, name, CONTAINER_PATH_SUFFIX);
        return Protos.CommandInfo.newBuilder()
                .setValue(cmd)
                .build();
    }
}
