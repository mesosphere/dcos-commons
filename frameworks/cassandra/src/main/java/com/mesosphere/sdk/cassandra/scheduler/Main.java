package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.cassandra.api.SeedsResource;
import com.mesosphere.sdk.config.validate.TaskEnvCannotChange;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Main entry point for the Scheduler.
 */
public final class Main {
  private static final String CUSTOM_YAML_BLOCK_BASE64_ENV = "TASKCFG_ALL_CUSTOM_YAML_BLOCK_BASE64";

  private Main() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException(
          "Expected one file argument, got: " + Arrays.toString(args)
      );
    }
    SchedulerRunner
        .fromSchedulerBuilder(createSchedulerBuilder(new File(args[0])))
        .run();
  }

  @SuppressWarnings("checkstyle:MultipleStringLiterals")
  private static SchedulerBuilder createSchedulerBuilder(File yamlSpecFile) throws Exception {
    SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
    RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(yamlSpecFile).build();
    List<String> localSeeds = CassandraSeedUtils
        .getLocalSeeds(rawServiceSpec.getName(), schedulerConfig);

    DefaultServiceSpec.Generator serviceSpecGenerator =
        DefaultServiceSpec.newGenerator(
            rawServiceSpec, schedulerConfig, yamlSpecFile.getParentFile())
            .setAllPodsEnv("LOCAL_SEEDS", Joiner.on(',').join(localSeeds));

    String yamlBase64 = System.getenv(CUSTOM_YAML_BLOCK_BASE64_ENV);
    if (yamlBase64 != null && yamlBase64.length() > 0) {
      String esYamlBlock = new String(
          Base64.getDecoder().decode(yamlBase64),
          StandardCharsets.UTF_8
      );
      serviceSpecGenerator.setAllPodsEnv("CUSTOM_YAML_BLOCK", esYamlBlock);
    }

    return DefaultScheduler.newBuilder(serviceSpecGenerator.build(), schedulerConfig)
        // Disallow changing the DC/Rack. Earlier versions of the Cassandra service didn't set these envvars so
        // we need to allow the case where they may have previously been unset:
        .setCustomConfigValidators(Arrays.asList(
            new CassandraZoneValidator(),
            new TaskEnvCannotChange("node", "server", "CASSANDRA_LOCATION_DATA_CENTER",
                TaskEnvCannotChange.Rule.ALLOW_UNSET_TO_SET),
            new TaskEnvCannotChange("node", "server", "CASSANDRA_LOCATION_RACK",
                TaskEnvCannotChange.Rule.ALLOW_UNSET_TO_SET)))
        .setPlansFrom(rawServiceSpec)
        .setCustomResources(getResources(localSeeds))
        .setRecoveryManagerFactory(new CassandraRecoveryPlanOverriderFactory())
        .withSingleRegionConstraint();
  }

  private static Collection<Object> getResources(List<String> localSeeds) {
    Collection<String> configuredSeeds = new ArrayList<>(localSeeds);

    String remoteSeeds = System.getenv("TASKCFG_ALL_REMOTE_SEEDS");
    if (!StringUtils.isEmpty(remoteSeeds)) {
      configuredSeeds.addAll(Arrays.asList(remoteSeeds.split(",")));
    }

    return Collections.singletonList(new SeedsResource(configuredSeeds));
  }
}
