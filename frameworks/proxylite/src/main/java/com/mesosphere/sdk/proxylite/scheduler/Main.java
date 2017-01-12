package com.mesosphere.sdk.proxylite.scheduler;

import com.mesosphere.sdk.specification.*;

import java.io.File;
import java.util.Collections;

/**
 * Hello World Service.
 */
public class Main {
    private static final Integer COUNT = Integer.valueOf(System.getenv("PROXYLITE_COUNT"));
    private static final Double CPUS = Double.valueOf(System.getenv("PROXYLITE_CPUS"));
    private static final String POD_TYPE = "proxylite";
    private static final String TASK_NAME = "proxylite";

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            new DefaultService(new File(args[0]));
        } else {
            // Example of building a custom ServiceSpec entirely in Java without a YAML file:
            new DefaultService(DefaultServiceSpec.newBuilder()
                    .name("proxylite")
                    .principal("proxylite-principal")
                    .zookeeperConnection("master.mesos:2181")
                    .apiPort(8080)
                    .addPod(DefaultPodSpec.newBuilder()
                            .count(COUNT)
                            .type(POD_TYPE)
                            .addTask(DefaultTaskSpec.newBuilder()
                                    .name(TASK_NAME)
                                    .goalState(GoalState.RUNNING)
                                    .commandSpec(DefaultCommandSpec.newBuilder(POD_TYPE)
                                            .value("echo proxylite >> proxylite-container-path/output && sleep 1000")
                                            .build())
                                    .resourceSet(DefaultResourceSet
                                            .newBuilder("proxylite-role", "proxylite-principal")
                                            .id("proxylite-resources")
                                            .cpus(CPUS)
                                            .memory(256.0)
                                            .addVolume("ROOT", 5000.0, "proxylite-container-path")
                                            .build()).build()).build()).build(),
                    Collections.emptyList());
        }
    }
}
