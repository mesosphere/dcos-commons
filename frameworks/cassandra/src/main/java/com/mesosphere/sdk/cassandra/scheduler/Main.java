package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.cassandra.api.SeedsResource;
import com.mesosphere.sdk.config.validate.TaskEnvCannotChange;
import com.mesosphere.sdk.framework.EnvStore;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ReplacementFailurePolicy;
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
  private static final String AUTHENTICATION_CUSTOM_YAML_BLOCK_BASE64_ENV =
      "TASKCFG_ALL_AUTHENTICATION_CUSTOM_YAML_BLOCK_BASE64";

  private Main() {
  }

  public static void main(String[] args) throws Exception {
    final EnvStore envStore = EnvStore.fromEnv();
    if (args.length != 1) {
      throw new IllegalArgumentException(
          "Expected one file argument, got: " + Arrays.toString(args)
      );
    }
    SchedulerRunner
        .fromSchedulerBuilder(createSchedulerBuilder(new File(args[0]), envStore))
        .run();
  }

  @SuppressWarnings("checkstyle:MultipleStringLiterals")
  private static SchedulerBuilder createSchedulerBuilder(File yamlSpecFile, EnvStore envStore) throws Exception {
    SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
    RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(yamlSpecFile).build();
    List<String> localSeeds = CassandraSeedUtils
        .getLocalSeeds(rawServiceSpec.getName(), schedulerConfig);

    DefaultServiceSpec.Generator serviceSpecGenerator =
        DefaultServiceSpec.newGenerator(
            rawServiceSpec, schedulerConfig, yamlSpecFile.getParentFile())
            .setAllPodsEnv("LOCAL_SEEDS", Joiner.on(',').join(localSeeds));

    String yamlBase64 = System.getenv(AUTHENTICATION_CUSTOM_YAML_BLOCK_BASE64_ENV);
    if (yamlBase64 != null && yamlBase64.length() > 0) {
      String yamlBlock = new String(
          Base64.getDecoder().decode(yamlBase64),
          StandardCharsets.UTF_8
      );
      serviceSpecGenerator.setAllPodsEnv("AUTHENTICATION_CUSTOM_YAML_BLOCK", yamlBlock);
    }

    DefaultServiceSpec serviceSpec = DefaultServiceSpec.newBuilder(serviceSpecGenerator.build())
            .replacementFailurePolicy(getReplacementFailurePolicy(envStore))
            .build();

    return DefaultScheduler.newBuilder(serviceSpec, schedulerConfig)
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

  private static ReplacementFailurePolicy getReplacementFailurePolicy(EnvStore envStore) throws Exception {
    if (envStore.getOptionalBoolean("ENABLE_AUTOMATIC_POD_REPLACEMENT", false)) {
      return ReplacementFailurePolicy.newBuilder()
          .permanentFailureTimoutSecs(Integer.valueOf(System.getenv("PERMANENT_FAILURE_TIMEOUT_SECS")))
          .minReplaceDelaySecs(Integer.valueOf(System.getenv("MIN_REPLACE_DELAY_SECS")))
          .build();
    } else {
      return null;
    }
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
