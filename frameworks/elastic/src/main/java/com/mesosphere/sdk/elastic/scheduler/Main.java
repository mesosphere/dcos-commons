package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.scheduler.*;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import java.io.File;
import java.util.Arrays;
import java.util.Base64;

/**
 * Main entry point for the Scheduler.
 */
public class Main {
    private static final String TASKCFG_ALL_CUSTOM_YAML_BLOCK_BASE64_ENV = "TASKCFG_ALL_CUSTOM_YAML_BLOCK_BASE64";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one file argument, got: " + Arrays.toString(args));
        }
        SchedulerRunner
                .fromSchedulerBuilder(createSchedulerBuilder(new File(args[0])))
                .run();
    }

    private static SchedulerBuilder createSchedulerBuilder(File yamlSpecFile) throws Exception {
        Base64.Decoder decoder = Base64.getDecoder();
        String base64_yaml = System.getenv(TASKCFG_ALL_CUSTOM_YAML_BLOCK_BASE64_ENV);
        byte[] es_yaml_bytes = decoder.decode(base64_yaml);
        String es_yaml_block = new String(es_yaml_bytes, "UTF-8");

        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(yamlSpecFile).build();
        SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
        // Modify pod environments in two ways:
        // 1) Elastic is unhappy if cluster.name contains slashes. Replace any slashes with double-underscores.
        // 2) Base64 decode the custom YAML block.

        return DefaultScheduler.newBuilder(
                DefaultServiceSpec.newGenerator(
                        rawServiceSpec, schedulerConfig, yamlSpecFile.getParentFile())
                        .setAllPodsEnv("CLUSTER_NAME", SchedulerUtils.withEscapedSlashes(rawServiceSpec.getName()))
                        .setAllPodsEnv("CUSTOM_YAML_BLOCK", es_yaml_block)
                        .build(),
                schedulerConfig)
                .setPlansFrom(rawServiceSpec);
    }
}
