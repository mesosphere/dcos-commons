package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.kafka.api.BrokerResource;
import com.mesosphere.sdk.kafka.api.KafkaZKClient;
import com.mesosphere.sdk.kafka.api.TopicResource;
import com.mesosphere.sdk.kafka.cmd.CmdExecutor;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.specification.ServiceSpec;

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
        super(pathToYamlSpecification);
    }

    @Override
    protected void startApiServer(DefaultScheduler defaultScheduler,
                                  int apiPort,
                                  Collection<Object> additionalResources) {
        final ServiceSpec serviceSpec = super.getServiceSpec();
        final Collection<Object> apiResources = new ArrayList<>();

        final KafkaZKClient kafkaZKClient = new KafkaZKClient(serviceSpec.getZookeeperConnection(),
                DcosConstants.SERVICE_ROOT_PATH_PREFIX + serviceSpec.getName());

        apiResources.add(new BrokerResource(kafkaZKClient));
        apiResources.add(new TopicResource(new CmdExecutor(kafkaZKClient, System.getenv("KAFKA_VERSION_PATH")),
                kafkaZKClient));

        apiResources.addAll(additionalResources);

        LOGGER.info("Starting API server with additional resources: {}", apiResources);
        super.startApiServer(defaultScheduler, apiPort, apiResources);
    }
}
