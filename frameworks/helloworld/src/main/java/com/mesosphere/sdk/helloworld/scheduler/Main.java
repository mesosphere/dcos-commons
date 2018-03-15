package com.mesosphere.sdk.helloworld.scheduler;

import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Main entry point for the Scheduler.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final Integer COUNT = Integer.valueOf(System.getenv("HELLO_COUNT"));
    private static final Double CPUS = Double.valueOf(System.getenv("HELLO_CPUS"));
    private static final String POD_TYPE = "hello";
    private static final String TASK_NAME = "hello";

    public static void main(String[] args) throws Exception {
        final SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
        final SchedulerRunner runner;
        File yamlSpecFile;
        ServiceSpec serviceSpec;
        SchedulerBuilder builder;

        Scenario scenario = getScenario();
        LOGGER.info("Scheduler operating under scenario: {}", scenario.name());

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
            case MULTI_REGION:
                yamlSpecFile = new File(args[0]);
                serviceSpec = DefaultServiceSpec
                        .newGenerator(yamlSpecFile, SchedulerConfig.fromEnv())
                        .build();
                builder = DefaultScheduler.newBuilder(serviceSpec, SchedulerConfig.fromEnv())
                        .withSingleRegionConstraint();
                runner = SchedulerRunner.fromSchedulerBuilder(builder);
                break;
            case CUSTOM_PLAN:
                yamlSpecFile = new File(args[0]);
                serviceSpec = DefaultServiceSpec
                        .newGenerator(yamlSpecFile, SchedulerConfig.fromEnv())
                        .build();
                builder = DefaultScheduler.newBuilder(serviceSpec, SchedulerConfig.fromEnv())
                        .setPlanCustomizer(new ReversePhasesCustomizer());
                runner = SchedulerRunner.fromSchedulerBuilder(builder);
                break;
            case CUSTOM_DECOMMISSION:
                yamlSpecFile = new File(args[0]);
                serviceSpec = DefaultServiceSpec
                        .newGenerator(yamlSpecFile, SchedulerConfig.fromEnv())
                        .build();
                builder = DefaultScheduler.newBuilder(serviceSpec, SchedulerConfig.fromEnv())
                        .setPlanCustomizer(new DecomissionCustomizer());
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
        CUSTOM_PLAN,
        CUSTOM_DECOMMISSION,
        MULTI_REGION
    }

    private static final String SCENARIO_KEY = "SCENARIO";
    private static final String YAML_FLAG = "YAML";
    private static final String JAVA_FLAG = "JAVA";
    private static final String CUSTOM_PLAN_FLAG = "CUSTOM_PLAN";
    private static final String CUSTOM_DECOMISSION_FLAG = "CUSTOM_DECOMMISSION";
    private static final String MULTI_REGION_FLAG = "MULTI_REGION";


    private static Scenario getScenario() {
        String flag = System.getenv().get(SCENARIO_KEY);
        LOGGER.info("Detected flag: {}", flag);
        switch (flag) {
            case JAVA_FLAG:
                return Scenario.Java;
            case CUSTOM_PLAN_FLAG:
                return Scenario.CUSTOM_PLAN;
            case CUSTOM_DECOMISSION_FLAG:
                return Scenario.CUSTOM_DECOMMISSION;
            case MULTI_REGION_FLAG:
                return Scenario.MULTI_REGION;
            case YAML_FLAG:
            default:
                return Scenario.YAML;
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
