package com.mesosphere.sdk.portworx.scheduler;

import com.mesosphere.sdk.config.validate.TaskEnvCannotChange;
import com.mesosphere.sdk.portworx.api.*;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import java.io.File;
import java.util.*;

/**
 * Portworx service.
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
        SchedulerBuilder schedulerBuilder = DefaultScheduler.newBuilder(
                DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerConfig, yamlSpecFile.getParentFile()).build(),
                schedulerConfig)
                .setCustomConfigValidators(Arrays.asList(
                        new TaskEnvCannotChange("etcd-cluster", "node", "ETCD_ENABLED"),
                        new TaskEnvCannotChange("etcd-proxy", "node", "ETCD_ENABLED"),
                        new TaskEnvCannotChange("lighthouse", "start", "LIGHTHOUSE_ENABLED")))
                .setPlansFrom(rawServiceSpec);

        schedulerBuilder.setCustomResources(getResources(schedulerBuilder.getServiceSpec()));
        return schedulerBuilder;
    }

    private static Collection<Object> getResources(ServiceSpec serviceSpec) {
        final Collection<Object> apiResources = new ArrayList<>();
        apiResources.add(new PortworxResource(serviceSpec));

        return apiResources;
    }
}
