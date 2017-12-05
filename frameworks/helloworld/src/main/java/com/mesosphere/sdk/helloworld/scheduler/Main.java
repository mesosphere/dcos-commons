package com.mesosphere.sdk.helloworld.scheduler;

import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import java.io.File;
import java.util.Arrays;

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
        File yamlSpecFile;

        Scenario scenario = getScenario(args);
        switch (scenario) {
            case Java:
                // Create a sample config in Java
                runner = SchedulerRunner.fromServiceSpec(
                        createSampleServiceSpec(schedulerConfig), schedulerConfig);
                break;
            case YAML:
                // Read config from provided file, and assume any config templates
                // are in the same directory as the file:
                yamlSpecFile = new File(args[0]);
                runner = SchedulerRunner.fromRawServiceSpec(
                        RawServiceSpec.newBuilder(yamlSpecFile).build(),
                        schedulerConfig,
                        yamlSpecFile.getParentFile());
                break;
            case CustomPlan:
                yamlSpecFile = new File(args[0]);
                ServiceSpec serviceSpec = DefaultServiceSpec
                        .newGenerator(yamlSpecFile, SchedulerConfig.fromEnv())
                        .build();
                SchedulerBuilder builder = DefaultScheduler.newBuilder(serviceSpec, SchedulerConfig.fromEnv());
                builder.setPlanCustomizer(new ReversePhasesCustomizer());
                runner = SchedulerRunner.fromSchedulerBuilder(builder);
                break;
            default:
                throw new IllegalStateException(String.format("Unexpected scnenario '%s'", scenario.name()));
        }

        runner.run();
    }

    private enum Scenario {
        YAML,
        Java,
        CustomPlan
    }

    private static Scenario getScenario(String[] args) {
        if (args.length == 0) {
            return Scenario.Java;
        } else if (args.length == 1) {
            if (Boolean.valueOf(System.getenv().get("CUSTOMIZE_DEPLOY_PLAN"))) {
                return Scenario.CustomPlan;
            } else {
                return Scenario.YAML;
            }
        } else {
            throw new IllegalArgumentException("Expected zero or one file argument, got: " + Arrays.toString(args));
        }

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
