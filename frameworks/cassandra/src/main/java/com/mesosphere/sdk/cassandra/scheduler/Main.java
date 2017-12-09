package com.mesosphere.sdk.cassandra.scheduler;

import com.google.common.base.Joiner;
import com.mesosphere.sdk.cassandra.api.SeedsResource;
import com.mesosphere.sdk.config.validate.TaskEnvCannotChange;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.*;

/**
 * Main entry point for the Scheduler.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one file argument, got: " + Arrays.toString(args));
        }
        SchedulerRunner
                .fromSchedulerBuilder(createSchedulerBuilder(new File(args[0])))
                .run();
    }

    private static SchedulerBuilder createSchedulerBuilder(File yamlSpecFile) throws Exception {
        SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(yamlSpecFile).build();
        List<String> localSeeds = CassandraSeedUtils.getLocalSeeds(rawServiceSpec.getName());
        return DefaultScheduler.newBuilder(
                DefaultServiceSpec
                        .newGenerator(rawServiceSpec, schedulerConfig, yamlSpecFile.getParentFile())
                        .setAllPodsEnv("LOCAL_SEEDS", Joiner.on(',').join(localSeeds))
                        .build(),
                schedulerConfig)
                // Disallow changing the DC/Rack. Earlier versions of the Cassandra service didn't set these envvars so
                // we need to allow the case where they may have previously been unset:
                .setCustomConfigValidators(Arrays.asList(
                        new CassandraZoneValidator(),
                        new TaskEnvCannotChange("node", "server", "CASSANDRA_LOCATION_DATA_CENTER",
                                TaskEnvCannotChange.Rule.ALLOW_UNSET_TO_SET)))
                .setPlansFrom(rawServiceSpec)
                .setCustomResources(getResources(localSeeds))
                .setRecoveryManagerFactory(new CassandraRecoveryPlanOverriderFactory());
    }

    private static Collection<Object> getResources(List<String> localSeeds) {
        Collection<String> configuredSeeds = new ArrayList<>(localSeeds);

        String remoteSeeds = System.getenv("TASKCFG_ALL_REMOTE_SEEDS");
        if (!StringUtils.isEmpty(remoteSeeds)) {
            configuredSeeds.addAll(Arrays.asList(remoteSeeds.split(",")));
        }

        return Arrays.asList(new SeedsResource(configuredSeeds));
    }
}
