package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.scheduler.*;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Main entry point for the Scheduler.
 */
public class Main {
    private static final String CUSTOM_YAML_BLOCK_BASE64_ENV = "CUSTOM_YAML_BLOCK_BASE64";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one file argument, got: " + Arrays.toString(args));
        }
        SchedulerRunner
                .fromSchedulerBuilder(createSchedulerBuilder(new File(args[0])))
                .run();
    }

    private static SchedulerBuilder createSchedulerBuilder(File yamlSpecFile) throws Exception {
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(yamlSpecFile).build();
        SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();

        // Modify pod environments in two ways:
        // 1) Elastic is unhappy if cluster.name contains slashes. Replace any slashes with double-underscores.
        // 2) Base64 decode the custom YAML block.

        DefaultServiceSpec.Generator serviceSpecGenerator =
                DefaultServiceSpec.newGenerator(
                        rawServiceSpec, schedulerConfig, yamlSpecFile.getParentFile())
                        .setAllPodsEnv("CLUSTER_NAME", SchedulerUtils.withEscapedSlashes(rawServiceSpec.getName()));

        String yamlBase64 = System.getenv(CUSTOM_YAML_BLOCK_BASE64_ENV);
        if (yamlBase64 != null && yamlBase64.length() > 0) {
            String esYamlBlock = new String(Base64.getDecoder().decode(yamlBase64), StandardCharsets.UTF_8);
            serviceSpecGenerator.setAllPodsEnv("CUSTOM_YAML_BLOCK", esYamlBlock);
        }

        return DefaultScheduler.newBuilder(serviceSpecGenerator.build(), schedulerConfig)
                .setPlansFrom(rawServiceSpec);
    }
}
