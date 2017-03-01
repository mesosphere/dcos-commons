package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.kafka.api.BrokerController;
import com.mesosphere.sdk.kafka.api.KafkaZKClient;
import com.mesosphere.sdk.kafka.api.TopicController;
import com.mesosphere.sdk.kafka.cmd.CmdExecutor;
import com.mesosphere.sdk.kafka.upgrade.CuratorStateStoreFilter;
import com.mesosphere.sdk.kafka.upgrade.KafkaConfigUpgrade;
import com.mesosphere.sdk.offer.evaluate.placement.RegexMatcher;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * Apache Kafka Service
 */
public class KafkaService extends DefaultService {
    protected static final Logger LOGGER = LoggerFactory.getLogger(KafkaService.class);


    public KafkaService(File pathToYamlSpecification) throws Exception {
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(pathToYamlSpecification);
        DefaultScheduler.Builder schedulerBuilder =
                DefaultScheduler.newBuilder(YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec));
        schedulerBuilder.setPlansFrom(rawServiceSpec);

        if (KafkaConfigUpgrade.enabled()){
            new KafkaConfigUpgrade(schedulerBuilder.getServiceSpec());
        }

        CuratorStateStoreFilter stateStore = new CuratorStateStoreFilter(schedulerBuilder.getServiceSpec().getName(),
                        DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
        stateStore.setIgnoreFilter(RegexMatcher.create("broker-[0-9]*"));
        schedulerBuilder.setStateStore(stateStore); //do not use StateStoreCache

        initService(schedulerBuilder);
    }

    @Override
    protected void startApiServer(DefaultScheduler defaultScheduler,
                                  int apiPort,
                                  Collection<Object> additionalResources) {
        final ServiceSpec serviceSpec = super.getServiceSpec();

        final Collection<Object> apiResources = new ArrayList<>();
        final String zkUri = String.format("%s/dcos-service-%s",
                serviceSpec.getZookeeperConnection(), serviceSpec.getName());
        final KafkaZKClient kafkaZKClient = new KafkaZKClient(zkUri);

        apiResources.add(new BrokerController(kafkaZKClient));
        apiResources.add(new TopicController(new CmdExecutor(kafkaZKClient, System.getenv("KAFKA_VERSION_PATH")),
                kafkaZKClient));

        apiResources.addAll(additionalResources);
        LOGGER.info("Starting API server with additional resources: {}", apiResources);
        super.startApiServer(defaultScheduler, apiPort, apiResources);
    }
}
