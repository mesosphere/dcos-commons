package com.mesosphere.sdk.helloworld.scheduler;

import org.apache.mesos.specification.*;

/**
 * Hello World Service.
 */
public class SimpleMain {
    public static void main(String[] args) throws Exception {
        new DefaultService(DefaultServiceSpec.newBuilder()
                .name("helloworld")
                .principal("helloworld-principal")
                .zookeeperConnection("master.mesos:2181")
                .apiPort(8080)
                .addPod(DefaultPodSpec.newBuilder()
                        .count(5)
                        .addTask(DefaultTaskSpec.newBuilder()
                                .name("hello")
                                .goalState(TaskSpec.GoalState.RUNNING)
                                .commandSpec(DefaultCommandSpec.newBuilder()
                                        .value("echo hello >> hello-container-path/output && sleep 1000")
                                        .build())
                                .resourceSet(DefaultResourceSet
                                        .newBuilder("helloworld-role", "helloworld-principal")
                                        .id("hello-resources")
                                        .cpus(1.0)
                                        .memory(256.0)
                                        .addVolume("ROOT", 5000.0, "hello-container-path")
                                        .build()).build()).build()).build());
    }
}
