package com.mesosphere.sdk.helloworld.scheduler;

import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

/**
 * Main entry point for the Scheduler.
 */
public class Main {
    private static final Integer COUNT = Integer.valueOf(System.getenv("HELLO_COUNT"));
    private static final Double CPUS = Double.valueOf(System.getenv("HELLO_CPUS"));
    private static final String POD_TYPE = "hello";
    private static final String TASK_NAME = "hello";

    public static void main(String[] args) throws Exception {
        final SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
        final SchedulerRunner runner;
        if (args.length == 0) {
            // Create a sample config in Java
            runner = SchedulerRunner.fromServiceSpec(
                    createSampleServiceSpec(schedulerConfig), schedulerConfig, Collections.emptyList());
        } else if (args.length == 1) {
            // Read config from provided file, and assume any config templates are in the same directory as the file:
            File yamlSpecFile = new File(args[0]);
            runner = SchedulerRunner.fromRawServiceSpec(
                    RawServiceSpec.newBuilder(yamlSpecFile).build(),
                    schedulerConfig,
                    yamlSpecFile.getParentFile());
        } else {
            throw new IllegalArgumentException("Expected zero or one file argument, got: " + Arrays.toString(args));
        }
        runner.run();
    }

    /**
     * Example of constructing a custom ServiceSpec in Java, without a YAML file.
     */
    private static ServiceSpec createSampleServiceSpec(SchedulerConfig schedulerConfig) {
        return DefaultServiceSpec.newBuilder()
                .name("hello-world")
                .principal("hello-world-principal")
                .zookeeperConnection("master.mesos:2181")
                .addPod(DefaultPodSpec.newBuilder(schedulerConfig.getExecutorURI())
                        .count(COUNT)
                        .type(POD_TYPE)
                        .addTask(DefaultTaskSpec.newBuilder()
                                .name(TASK_NAME)
                                .goalState(GoalState.RUNNING)
                                .commandSpec(DefaultCommandSpec.newBuilder(new TaskEnvRouter().getConfig(POD_TYPE))
                                        .value("echo hello >> hello-container-path/output && sleep 1000")
                                        .build())
                                .resourceSet(DefaultResourceSet
                                        .newBuilder("hello-world-role", Constants.ANY_ROLE, "hello-world-principal")
                                        .id("hello-resources")
                                        .cpus(CPUS)
                                        .memory(256.0)
                                        .addVolume("ROOT", 5000.0, "hello-container-path")
                                        .build())
                                .build())
                        .build())
                .build();
    }
}
