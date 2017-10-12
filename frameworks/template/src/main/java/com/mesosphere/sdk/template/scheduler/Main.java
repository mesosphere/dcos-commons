package com.mesosphere.sdk.template.scheduler;

import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import java.io.File;
import java.util.Arrays;

/**
 * Main entry point for the Scheduler.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one file argument, got: " + Arrays.toString(args));
        }

        // Read config from provided file, and assume any config templates are in the same directory as the file:
        File yamlSpecFile = new File(args[0]);
        SchedulerRunner
                .fromRawServiceSpec(
                        RawServiceSpec.newBuilder(yamlSpecFile).build(),
                        SchedulerConfig.fromEnv(),
                        yamlSpecFile.getParentFile())
                .run();
    }
}
