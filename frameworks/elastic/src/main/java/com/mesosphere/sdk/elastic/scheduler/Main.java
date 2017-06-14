package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.specification.DefaultService;
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
        if (args.length > 0) {
            RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(new File(args[0])).build();
            SchedulerFlags schedulerFlags = SchedulerFlags.fromEnv();
            // Elastic is unhappy if cluster.name contains slashes. Replace any slashes with double-underscores:
            DefaultScheduler.Builder schedulerBuilder = DefaultScheduler.newBuilder(
                    DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerFlags)
                            .setAllPodsEnv("CLUSTER_NAME", SchedulerUtils.withEscapedSlashes(rawServiceSpec.getName()))
                            .build(),
                    schedulerFlags)
                    .setPlansFrom(rawServiceSpec);
            new DefaultService(schedulerBuilder).run();
        } else {
            LOGGER.error("Missing file argument");
            System.exit(1);
        }
    }
}
