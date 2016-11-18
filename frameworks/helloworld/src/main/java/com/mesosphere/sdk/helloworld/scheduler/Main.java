package com.mesosphere.sdk.helloworld.scheduler;

import com.google.common.collect.ImmutableMap;
import org.apache.mesos.dcos.DcosConstants;
import org.apache.mesos.specification.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Multi-Pod Hello World Service.
 */
public class Main {
    private static final String SLEEP_DURATION_KEY = "SLEEP_DURATION";
    private static final String ROLE = "hello-world-role";
    private static final String PRINCIPAL = "hello-world-principal";
    private static final Integer PORT0 = Integer.valueOf(System.getenv("PORT0"));
    private static final Integer HELLO_COUNT = Integer.valueOf(System.getenv("HELLO_COUNT"));
    private static final Integer WORLD_COUNT = Integer.valueOf(System.getenv("WORLD_COUNT"));
    private static final String SLEEP_DURATION = System.getenv(SLEEP_DURATION_KEY);
    private static final Double HELLO_CPUS = Double.valueOf(System.getenv("HELLO_CPUS"));
    private static final Double HELLO_MEM = Double.valueOf(System.getenv("HELLO_MEM"));
    private static final Double HELLO_DISK = Double.valueOf(System.getenv("HELLO_DISK"));
    private static final Double WORLD_CPUS = Double.valueOf(System.getenv("WORLD_CPUS"));
    private static final Double WORLD_MEM = Double.valueOf(System.getenv("WORLD_MEM"));
    private static final Double WORLD_DISK = Double.valueOf(System.getenv("WORLD_DISK"));

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            new DefaultService(new File(args[0]));
        } else {
            new DefaultService(getServiceSpec());
        }
    }

    private static ServiceSpec getServiceSpec() {
        return DefaultServiceSpec.newBuilder()
                .name("hello-world")
                .principal("hello-world-principal")
                .zookeeperConnection(DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING)
                .apiPort(PORT0)
                .pods(getPodSpecs())
                .build();
    }

    private static List<PodSpec> getPodSpecs() {
        PodSpec helloPod = DefaultPodSpec.newBuilder()
                .count(HELLO_COUNT)
                .tasks(getHelloTask())
                .build();
        PodSpec worldPod = DefaultPodSpec.newBuilder()
                .count(WORLD_COUNT)
                .tasks(getWorldTask())
                .build();
        return Arrays.asList(helloPod, worldPod);
    }

    private static List<TaskSpec> getHelloTask() {
        ResourceSet helloResourceSet = DefaultResourceSet.newBuilder(ROLE, PRINCIPAL)
                .id("hello-resources")
                .cpus(HELLO_CPUS)
                .memory(HELLO_MEM)
                .addVolume("ROOT", HELLO_DISK, "hello-container-path")
                .build();
        TaskSpec helloTask = DefaultTaskSpec.newBuilder()
                .name("hello")
                .goalState(TaskSpec.GoalState.RUNNING)
                .commandSpec(DefaultCommandSpec.newBuilder()
                        .value("echo hello >> hello-container-path/output && sleep $SLEEP_DURATION")
                        .environment(ImmutableMap.of(SLEEP_DURATION_KEY, SLEEP_DURATION))
                        .build())
                .resourceSet(helloResourceSet)
                .build();
        return Arrays.asList(helloTask);
    }

    private static List<TaskSpec> getWorldTask() {
        ResourceSet helloResourceSet = DefaultResourceSet.newBuilder(ROLE, PRINCIPAL)
                .id("world-resources")
                .cpus(WORLD_CPUS)
                .memory(WORLD_MEM)
                .addVolume("ROOT", WORLD_DISK, "world-container-path")
                .build();
        TaskSpec worldTask = DefaultTaskSpec.newBuilder()
                .name("world")
                .goalState(TaskSpec.GoalState.RUNNING)
                .commandSpec(DefaultCommandSpec.newBuilder()
                        .value("echo world >> world-container-path/output && sleep $SLEEP_DURATION")
                        .environment(ImmutableMap.of(SLEEP_DURATION_KEY, SLEEP_DURATION))
                        .build())
                .resourceSet(helloResourceSet)
                .build();
        return Arrays.asList(worldTask);
    }
}
