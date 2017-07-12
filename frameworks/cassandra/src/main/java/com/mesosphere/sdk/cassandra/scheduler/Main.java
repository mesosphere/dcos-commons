package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.cassandra.api.SeedsResource;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.StateStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Cassandra Service.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        new DefaultService(createSchedulerBuilder(new File(args[0]))).run();
    }

    private static DefaultScheduler.Builder createSchedulerBuilder(File pathToYamlSpecification)
            throws Exception {
        SchedulerFlags schedulerFlags = SchedulerFlags.fromEnv();
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(pathToYamlSpecification).build();
        DefaultScheduler.Builder schedulerBuilder = DefaultScheduler.newBuilder(
                DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerFlags)
                        .build(),
                schedulerFlags)
                .setPlansFrom(rawServiceSpec)
                .setRecoveryManagerFactory(new CassandraRecoveryPlanOverriderFactory());
        schedulerBuilder.setCustomResources(getResources(schedulerBuilder.getStateStore()));

        return schedulerBuilder;
    }

    private static Collection<Object> getResources(StateStore stateStore) {
        final Collection<Object> apiResources = new ArrayList<>();
        apiResources.add(new SeedsResource(stateStore, 2));

        return apiResources;
    }
}
