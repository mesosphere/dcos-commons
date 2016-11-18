package com.mesosphere.sdk.helloworld.scheduler;

import org.apache.mesos.dcos.DcosConstants;
import org.apache.mesos.specification.*;

import java.io.File;
import java.util.*;

/**
 * Main.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            new DefaultService(new File(args[0]));
        } else {
            ServiceSpec serviceSpec = getServiceSpec();
            DefaultService defaultService = new DefaultService();
            defaultService.register(serviceSpec, Collections.emptyList());
        }
    }

    private static final String SLEEP_DURATION_KEY = "SLEEP_DURATION";
    private static final String ROLE = "hello-world-role";
    private static final String PRINCIPAL = "hello-world-principal";

    public static ServiceSpec getServiceSpec() {
        return DefaultServiceSpec.newBuilder()
                .name("hello-world")
                .principal("hello-world-principal")
                .zookeeperConnection(DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING)
                .apiPort(Integer.valueOf(System.getenv("PORT0")))
                .pods(getPodSpecs())
                .build();
    }

    public static List<PodSpec> getPodSpecs() {
        PodSpec helloPod = DefaultPodSpec.newBuilder()
                .count(Integer.valueOf(System.getenv("HELLO_COUNT")))
                .tasks(getHelloTask())
                .build();

        PodSpec worldPod = DefaultPodSpec.newBuilder()
                .count(Integer.valueOf(System.getenv("WORLD_COUNT")))
                .tasks(getWorldTask())
                .build();

        return Arrays.asList(helloPod, worldPod);
    }


    public static List<TaskSpec> getHelloTask() {
        Map<String, String> env = new HashMap<>();
        env.put(SLEEP_DURATION_KEY, System.getenv(SLEEP_DURATION_KEY));

        ResourceSet helloResourceSet = DefaultResourceSet.newBuilder(ROLE, PRINCIPAL)
                .id("hello-resources")
                .cpus(Double.valueOf(System.getenv("HELLO_CPUS")))
                .memory(Double.valueOf(System.getenv("HELLO_MEM")))
                .addVolume("ROOT", Double.valueOf(System.getenv("HELLO_DISK")), "hello-container-path")
                .build();

        TaskSpec helloTask = DefaultTaskSpec.newBuilder()
                .name("hello")
                .goalState(TaskSpec.GoalState.RUNNING)
                .commandSpec(DefaultCommandSpec.newBuilder()
                        .value("echo hello >> hello-container-path/output && sleep $SLEEP_DURATION")
                        .environment(env)
                        .build())
                .resourceSet(helloResourceSet)
                .build();

        return Arrays.asList(helloTask);
    }

    public static List<TaskSpec> getWorldTask() {
        Map<String, String> env = new HashMap<>();
        env.put(SLEEP_DURATION_KEY, System.getenv(SLEEP_DURATION_KEY));

        ResourceSet helloResourceSet = DefaultResourceSet.newBuilder(ROLE, PRINCIPAL)
                .id("world-resources")
                .cpus(Double.valueOf(System.getenv("WORLD_CPUS")))
                .memory(Double.valueOf(System.getenv("WORLD_MEM")))
                .addVolume("ROOT", Double.valueOf(System.getenv("WORLD_DISK")), "world-container-path")
                .build();

        TaskSpec worldTask = DefaultTaskSpec.newBuilder()
                .name("world")
                .goalState(TaskSpec.GoalState.RUNNING)
                .commandSpec(DefaultCommandSpec.newBuilder()
                        .value("echo world >> world-container-path/output && sleep $SLEEP_DURATION")
                        .environment(env)
                        .build())
                .resourceSet(helloResourceSet)
                .build();

        return Arrays.asList(worldTask);
    }
}
