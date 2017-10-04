package com.mesosphere.sdk.kafka.scheduler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.curator.CuratorUtils;
import com.mesosphere.sdk.kafka.api.BrokerResource;
import com.mesosphere.sdk.kafka.api.KafkaZKClient;
import com.mesosphere.sdk.kafka.api.TopicResource;
import com.mesosphere.sdk.kafka.cmd.CmdExecutor;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

/**
 * Main entry point for the Scheduler.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final String KAFKA_ZK_URI_ENV = "KAFKA_ZOOKEEPER_URI";

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

        // Allow users to manually specify a ZK location for kafka itself. Otherwise default to our service ZK location:
        String kafkaZookeeperUri = System.getenv(KAFKA_ZK_URI_ENV);
        if (StringUtils.isEmpty(kafkaZookeeperUri)) {
            // "master.mesos:2181" + "/dcos-service-path__to__my__kafka":
            kafkaZookeeperUri =
                    SchedulerUtils.getZkHost(rawServiceSpec, schedulerConfig)
                    + CuratorUtils.getServiceRootPath(rawServiceSpec.getName());
        }
        LOGGER.info("Running Kafka with zookeeper path: {}", kafkaZookeeperUri);

        SchedulerBuilder schedulerBuilder = DefaultScheduler.newBuilder(
                DefaultServiceSpec
                        .newGenerator(rawServiceSpec, schedulerConfig, yamlSpecFile.getParentFile())
                        .setAllPodsEnv(KAFKA_ZK_URI_ENV, kafkaZookeeperUri)
                        .build(),
                schedulerConfig)
                .setPlansFrom(rawServiceSpec);

        return schedulerBuilder
                .setEndpointProducer("zookeeper", EndpointProducer.constant(kafkaZookeeperUri))
                .setCustomResources(getResources(kafkaZookeeperUri));
    }

    private static Collection<Object> getResources(String kafkaZookeeperUri) {
        KafkaZKClient kafkaZKClient = new KafkaZKClient(kafkaZookeeperUri);
        final Collection<Object> apiResources = new ArrayList<>();
        apiResources.add(new BrokerResource(kafkaZKClient));
        apiResources.add(new TopicResource(
                new CmdExecutor(kafkaZKClient, kafkaZookeeperUri, System.getenv("KAFKA_VERSION_PATH")),
                kafkaZKClient));
        return apiResources;
    }
}
