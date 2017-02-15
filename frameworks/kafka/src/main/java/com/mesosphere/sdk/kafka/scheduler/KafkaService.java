package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.kafka.api.BrokerController;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultService;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

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
        final Collection<Object> apiResources = new ArrayList<>();
        apiResources.add(new BrokerController(System.getenv("TASKCFG_ALL_KAFKA_ZOOKEEPER_URI")));
        if (CollectionUtils.isNotEmpty(additionalResources)) {
            apiResources.addAll(additionalResources);
        }
        LOGGER.info("Starting API server with resources: {}", apiResources);
        super.startApiServer(defaultScheduler, apiPort, additionalResources);
    }
}
