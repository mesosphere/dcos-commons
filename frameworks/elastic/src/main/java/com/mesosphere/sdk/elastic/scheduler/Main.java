package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;


/**
 * Main entry point for the Scheduler.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            LOGGER.error("Missing file argument");
            System.exit(1);
        }
        File pathToYamlSpecification = new File(args[0]);
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(pathToYamlSpecification).build();
        SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
        // Elastic is unhappy if cluster.name contains slashes. Replace any slashes with double-underscores:
        SchedulerBuilder schedulerBuilder = DefaultScheduler.newBuilder(
                DefaultServiceSpec.newGenerator(
                        rawServiceSpec, schedulerConfig, pathToYamlSpecification.getParentFile())
                        .setAllPodsEnv("CLUSTER_NAME", SchedulerUtils.withEscapedSlashes(rawServiceSpec.getName()))
                        .build(),
                schedulerConfig)
                .setPlansFrom(rawServiceSpec);
        SchedulerRunner.fromSchedulerBuilder(schedulerBuilder).run();
    }
}
