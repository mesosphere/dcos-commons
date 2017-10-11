package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Base64;


/**
 * Main entry point for the Scheduler.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final String TASKCFG_ALL_CUSTOM_YAML_BLOCK_BASE64_ENV = "TASKCFG_ALL_CUSTOM_YAML_BLOCK_BASE64";

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            Base64.Decoder decoder = Base64.getDecoder();
            String base64_yaml = System.getenv(TASKCFG_ALL_CUSTOM_YAML_BLOCK_BASE64_ENV);
            byte[] es_yaml_bytes = decoder.decode(base64_yaml);
            String es_yaml_block = new String(es_yaml_bytes, "UTF-8");

            File pathToYamlSpecification = new File(args[0]);
            RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(pathToYamlSpecification).build();
            SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
            // Modify pod environments in two ways:
            // 1) Elastic is unhappy if cluster.name contains slashes. Replace any slashes with double-underscores.
            // 2) Base64 decode the custom YAML block.
            DefaultScheduler.Builder schedulerBuilder = DefaultScheduler.newBuilder(
                    DefaultServiceSpec.newGenerator(
                            rawServiceSpec, schedulerConfig, pathToYamlSpecification.getParentFile())
                            .setAllPodsEnv("CLUSTER_NAME", SchedulerUtils.withEscapedSlashes(rawServiceSpec.getName()))
                            .setAllPodsEnv("CUSTOM_YAML_BLOCK", es_yaml_block)
                            .build(),
                    schedulerConfig)
                    .setPlansFrom(rawServiceSpec);
            new DefaultService(schedulerBuilder).run();
        } else {
            LOGGER.error("Missing file argument");
            System.exit(1);
        }
    }
}
