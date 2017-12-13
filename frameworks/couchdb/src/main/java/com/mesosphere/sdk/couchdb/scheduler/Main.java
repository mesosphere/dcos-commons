package com.mesosphere.sdk.couchdb.scheduler;

import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import java.io.File;
import java.util.*;

/**
 * CouchDB service.
 */

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one file argument, got: " + Arrays.toString(args));
        }

        File yamlSpecFile = new File(args[0]);
        SchedulerRunner
                .fromRawServiceSpec(
                        RawServiceSpec.newBuilder(yamlSpecFile).build(),
                        SchedulerConfig.fromEnv(),
                        yamlSpecFile.getParentFile())
                .run();
    }
}
