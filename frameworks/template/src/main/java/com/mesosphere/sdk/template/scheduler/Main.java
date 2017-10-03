package com.mesosphere.sdk.template.scheduler;

import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Template service.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            LOGGER.error("Missing file argument");
            System.exit(1);
        }
        File pathToYamlSpecification = new File(args[0]);
        SchedulerRunner.fromRawServiceSpec(
                RawServiceSpec.newBuilder(pathToYamlSpecification).build(),
                SchedulerConfig.fromEnv(),
                pathToYamlSpecification.getParentFile()).run();
    }
}
