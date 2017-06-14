package com.mesosphere.sdk.cassandra.scheduler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Joiner;
import com.mesosphere.sdk.api.EndpointUtils;
import com.mesosphere.sdk.cassandra.api.SeedsResource;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

/**
 * Cassandra Service.
 */
public class Main {
    private static final int SEEDS_COUNT;
    static {
        String seedsCount = System.getenv("LOCAL_SEEDS_COUNT");
        if (seedsCount == null) {
            seedsCount = "2";
        }
        SEEDS_COUNT = Integer.parseInt(seedsCount);
    }

    public static void main(String[] args) throws Exception {
        new DefaultService(createSchedulerBuilder(new File(args[0]))).run();
    }

    private static DefaultScheduler.Builder createSchedulerBuilder(File pathToYamlSpecification)
            throws Exception {
        SchedulerFlags schedulerFlags = SchedulerFlags.fromEnv();
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(pathToYamlSpecification).build();
        List<String> localSeeds = getLocalSeeds(rawServiceSpec.getName());
        DefaultScheduler.Builder schedulerBuilder = DefaultScheduler.newBuilder(
                DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerFlags)
                        .setAllPodsEnv("LOCAL_SEEDS", Joiner.on(',').join(localSeeds))
                        .build(),
                schedulerFlags)
                .setPlansFrom(rawServiceSpec)
                .setCustomResources(getResources(localSeeds))
                .setRecoveryManagerFactory(new CassandraRecoveryPlanOverriderFactory());
        return schedulerBuilder;
    }

    private static List<String> getLocalSeeds(String serviceName) {
        List<String> localSeeds = new ArrayList<>();
        for (int i = 0; i < SEEDS_COUNT; ++i) {
            // for example, 'node-0-server.<svcname>.autoip...,node-1-server.<svcname>.autoip...'
            localSeeds.add(EndpointUtils.toAutoIpHostname(serviceName, String.format("node-%d-server", i)));
        }
        return localSeeds;
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
