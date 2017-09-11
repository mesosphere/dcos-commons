package com.mesosphere.sdk.template.scheduler;

import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import java.io.File;

/**
 * Template service.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        File pathToYamlSpecification = new File(args[0]);
        SchedulerRunner.fromRawServiceSpec(
                RawServiceSpec.newBuilder(pathToYamlSpecification).build(),
                SchedulerFlags.fromEnv(),
                pathToYamlSpecification.getParentFile()).run();
    }
}
