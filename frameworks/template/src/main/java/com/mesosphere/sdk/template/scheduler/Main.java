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

        File yamlSpecFile = new File(args[0]);
        // Assume that any configuration template files are in the same directory as the YAML specification file:
        File configTemplateDir = yamlSpecFile.getParentFile();

        // Build and run the service:
        SchedulerRunner
                .fromRawServiceSpec(
                        RawServiceSpec.newBuilder(yamlSpecFile).build(),
                        SchedulerConfig.fromEnv(),
                        configTemplateDir)
                .run();
    }
}
