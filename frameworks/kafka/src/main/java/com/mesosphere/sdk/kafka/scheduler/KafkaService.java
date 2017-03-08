package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.kafka.api.*;
import com.mesosphere.sdk.kafka.cmd.CmdExecutor;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * Kafka Service.
 */
public class KafkaService extends DefaultService {
    protected static final Logger LOGGER = LoggerFactory.getLogger(KafkaService.class);

    public KafkaService(File pathToYamlSpecification) throws Exception {
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(pathToYamlSpecification);
        DefaultScheduler.Builder schedulerBuilder =
                DefaultScheduler.newBuilder(YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec));
        schedulerBuilder.setPlansFrom(rawServiceSpec);

        schedulerBuilder.setEndpointProducer("zookeeper", EndpointProducer.constant(
                schedulerBuilder.getServiceSpec().getZookeeperConnection() +
                DcosConstants.SERVICE_ROOT_PATH_PREFIX + schedulerBuilder.getServiceSpec().getName()));

        initService(schedulerBuilder);
    }

    @Override
    protected void startApiServer(DefaultScheduler defaultScheduler,
                                  int apiPort,
                                  Collection<Object> additionalResources) {
        final Collection<Object> apiResources = new ArrayList<>();

        KafkaZKClient kafkaZKClient = new KafkaZKClient(super.getServiceSpec().getZookeeperConnection(),
                DcosConstants.SERVICE_ROOT_PATH_PREFIX + super.getServiceSpec().getName());

        apiResources.add(new BrokerResource(kafkaZKClient));
        apiResources.add(new TopicResource(new CmdExecutor(kafkaZKClient, System.getenv("KAFKA_VERSION_PATH")),
                kafkaZKClient));

        apiResources.addAll(additionalResources);

        LOGGER.info("Starting API server with additional resources: {}", apiResources);
        super.startApiServer(defaultScheduler, apiPort, apiResources);
    }
}
