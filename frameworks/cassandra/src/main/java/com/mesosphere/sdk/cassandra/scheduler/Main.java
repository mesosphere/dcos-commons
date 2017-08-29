package com.mesosphere.sdk.cassandra.scheduler;

import com.google.common.base.Joiner;
import com.mesosphere.sdk.cassandra.api.SeedsResource;
import com.mesosphere.sdk.config.validate.TaskEnvCannotChange;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * Cassandra Service.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) throws Exception {
        new DefaultService(createSchedulerBuilder(new File(args[0]))).run();
    }

    private static DefaultScheduler.Builder createSchedulerBuilder(File pathToYamlSpecification)
            throws Exception {
        SchedulerFlags schedulerFlags = SchedulerFlags.fromEnv();
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(pathToYamlSpecification).build();
        List<String> localSeeds = CassandraSeedUtils.getLocalSeeds(rawServiceSpec.getName());
        return DefaultScheduler.newBuilder(
                DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerFlags)
                        .setAllPodsEnv("LOCAL_SEEDS", Joiner.on(',').join(localSeeds))
                        .build(),
                schedulerFlags)
                // Disallow changing the DC/Rack. Earlier versions of the Cassandra service didn't set these envvars so
                // we need to allow the case where they may have previously been unset:
                .setCustomConfigValidators(Arrays.asList(
                        new TaskEnvCannotChange("node", "server", "CASSANDRA_LOCATION_DATA_CENTER",
                                TaskEnvCannotChange.Rule.ALLOW_UNSET_TO_SET),
                        new TaskEnvCannotChange("node", "server", "CASSANDRA_LOCATION_RACK",
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
