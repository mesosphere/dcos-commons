package com.mesosphere.sdk.helloworld.scheduler;

import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.curator.CuratorPersister;
import com.mesosphere.sdk.framework.EnvStore;
import com.mesosphere.sdk.framework.FrameworkConfig;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.specification.DefaultCommandSpec;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.DefaultResourceSet;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.DefaultTaskSpec;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterCache;
import com.mesosphere.sdk.storage.PersisterException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Main entry point for the Scheduler.
 */
public final class Main {

  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private static final String HELLO_COUNT_ENV_KEY = "HELLO_COUNT";

  private static final String HELLO_CPUS_ENV_KEY = "HELLO_CPUS";

  private static final String FRAMEWORK_GPUS_ENV_KEY = "FRAMEWORK_GPUS";

  private static final String YAML_DIR = "hello-world-scheduler/";

  private static final String YAML_EXT = ".yml";


  private Main() {}

  public static void main(String[] args) throws Exception {
    final EnvStore envStore = EnvStore.fromEnv();
    final SchedulerConfig schedulerConfig = SchedulerConfig.fromEnvStore(envStore);

    final Collection<Scenario.Type> scenarios = Scenario.getScenarios(envStore);
    LOGGER.info("Using scenarios: {}", scenarios);
    if (scenarios.contains(Scenario.Type.JAVA)) {
      // Create a sample spec in Java, ignore any yaml file args
      LOGGER.info("Starting Java-defined scheduler");
      runJavaDefinedService(schedulerConfig, envStore, scenarios);
      return;
    }

    if (args.length == 1) {
      File yamlFile = new File(YAML_DIR, args[0] + YAML_EXT);
      // One YAML file: Mono-Scheduler
      LOGGER.info("Starting mono-scheduler using: {}", yamlFile);
      runSingleYamlService(schedulerConfig, yamlFile, scenarios);
    }
  }

  private static void runJavaDefinedService(
      SchedulerConfig schedulerConfig, EnvStore envStore, Collection<Scenario.Type> scenarios)
      throws PersisterException
  {
    ServiceSpec javaServiceSpec = createSampleServiceSpec(schedulerConfig, envStore);
    SchedulerBuilder builder = DefaultScheduler.newBuilder(javaServiceSpec, schedulerConfig);
    SchedulerRunner
        .fromSchedulerBuilder(Scenario.customize(builder, Optional.empty(), scenarios)).run();
  }

  /**
   * Starts a scheduler which runs a single fixed service.
   */
  private static void runSingleYamlService(
      SchedulerConfig schedulerConfig, File yamlFile, Collection<Scenario.Type> scenarios)
      throws Exception
  {
    RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(yamlFile).build();
    ServiceSpec serviceSpec = DefaultServiceSpec
        .newGenerator(rawServiceSpec, schedulerConfig, yamlFile.getParentFile())
        .build();
    Persister persister =
        getPersister(schedulerConfig, FrameworkConfig.fromServiceSpec(serviceSpec));
    SchedulerBuilder builder = DefaultScheduler
        .newBuilder(serviceSpec, schedulerConfig, persister)
        .setPlansFrom(rawServiceSpec);
    SchedulerRunner
        .fromSchedulerBuilder(Scenario.customize(builder, Optional.empty(), scenarios))
        .run();
  }

  private static Persister getPersister(
      SchedulerConfig schedulerConfig,
      FrameworkConfig frameworkConfig)
  {
    Persister persister = CuratorPersister
        .newBuilder(frameworkConfig.getFrameworkName(), frameworkConfig.getZookeeperHostPort())
        .build();
    if (schedulerConfig.isStateCacheEnabled()) {
      persister = new PersisterCache(persister, schedulerConfig);
    }
    return persister;
  }

  /**
   * Example of constructing a custom ServiceSpec in Java, without a YAML file.
   */
  @SuppressWarnings({
      "checkstyle:MagicNumber",
      "checkstyle:MultipleStringLiterals"
  })
  private static ServiceSpec createSampleServiceSpec(
      SchedulerConfig schedulerConfig,
      EnvStore envStore)
  {
    String podType = "hello";
    String taskName = "hello";

    return DefaultServiceSpec.newBuilder()
        .name("hello-world")
        .principal("hello-world-principal")
        .zookeeperConnection("master.mesos:2181")
        .addPod(DefaultPodSpec.newBuilder(
            podType,
            envStore.getRequiredInt(HELLO_COUNT_ENV_KEY),
            Collections.singletonList(DefaultTaskSpec.newBuilder()
                .name(taskName)
                .goalState(GoalState.RUNNING)
                .commandSpec(DefaultCommandSpec.newBuilder(new TaskEnvRouter().getConfig(podType))
                    .value("echo hello >> hello-container-path/output && sleep 1000")
                    .build())
                .resourceSet(DefaultResourceSet
                    .newBuilder("hello-world-role", Constants.ANY_ROLE, "hello-world-principal")
                    .id("hello-resources")
                    .cpus(Double.valueOf(envStore.getRequired(HELLO_CPUS_ENV_KEY)))
                    .memory(256.0)
                    .addRootVolume(5000.0, "hello-container-path")
                    .build())
                .build()))
            .build())
        .build();
  }
}
