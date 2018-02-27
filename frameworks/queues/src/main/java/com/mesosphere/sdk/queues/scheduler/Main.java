package com.mesosphere.sdk.queues.scheduler;

import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.JobsEventClient;
import com.mesosphere.sdk.scheduler.QueueRunner;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.ServiceScheduler;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import java.io.File;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Queues service.
 */
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected at least one file argument, got: " + Arrays.toString(args));
        }

        SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
        JobsEventClient client = new JobsEventClient();

        // Read jobs from provided files, and assume any config templates are in the same directory as the files:
        for (int i = 0; i < args.length; ++i) {
            LOGGER.info("Reading job from spec file: {}", args[i]);
            File yamlSpecFile = new File(args[i]);
            RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(yamlSpecFile).build();
            ServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(
                    rawServiceSpec, schedulerConfig, yamlSpecFile.getParentFile()).build();
            LOGGER.info("Adding job: {}", serviceSpec.getName());
            ServiceScheduler jobScheduler = DefaultScheduler.newBuilder(serviceSpec, schedulerConfig)
                    .setPlansFrom(rawServiceSpec)
                    .build();
            client.putJob(serviceSpec.getName(), jobScheduler);
        }

        QueueRunner.build(client).run();
    }
}
