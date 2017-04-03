package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.kafka.api.*;
import com.mesosphere.sdk.kafka.cmd.CmdExecutor;
import com.mesosphere.sdk.kafka.upgrade.CuratorStateStoreFilter;
import com.mesosphere.sdk.kafka.upgrade.KafkaConfigUpgrade;
import com.mesosphere.sdk.offer.evaluate.placement.RegexMatcher;
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

        /* Upgrade */
        new KafkaConfigUpgrade(schedulerBuilder.getServiceSpec());
        CuratorStateStoreFilter stateStore = new CuratorStateStoreFilter(schedulerBuilder.getServiceSpec().getName(),
                DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
        stateStore.setIgnoreFilter(RegexMatcher.create("broker-[0-9]*"));
        schedulerBuilder.setStateStore(stateStore);
        /* Upgrade */

        schedulerBuilder.setEndpointProducer("zookeeper", EndpointProducer.constant(
                schedulerBuilder.getServiceSpec().getZookeeperConnection() +
                        DcosConstants.SERVICE_ROOT_PATH_PREFIX + schedulerBuilder.getServiceSpec().getName()));

        initService(schedulerBuilder);
        schedulerBuilder.setResources(getResources());
    }

    private Collection<Object> getResources() {
        KafkaZKClient kafkaZKClient = new KafkaZKClient(
                super.getServiceSpec().getZookeeperConnection(),
                DcosConstants.SERVICE_ROOT_PATH_PREFIX + super.getServiceSpec().getName());

        final Collection<Object> apiResources = new ArrayList<>();
        apiResources.add(new BrokerResource(kafkaZKClient));
        apiResources.add(new TopicResource(
                new CmdExecutor(kafkaZKClient, System.getenv("KAFKA_VERSION_PATH")),
                kafkaZKClient));

        return apiResources;
    }
}
