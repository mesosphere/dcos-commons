package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.curator.CuratorPersister;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.kafka.api.*;
import com.mesosphere.sdk.kafka.cmd.CmdExecutor;
import com.mesosphere.sdk.kafka.upgrade.FilterStateStore;
import com.mesosphere.sdk.kafka.upgrade.KafkaConfigUpgrade;
import com.mesosphere.sdk.offer.evaluate.placement.RegexMatcher;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterCache;

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
        SchedulerFlags schedulerFlags = SchedulerFlags.fromEnv();
        DefaultScheduler.Builder schedulerBuilder = DefaultScheduler.newBuilder(
                YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, schedulerFlags), schedulerFlags)
                .setPlansFrom(rawServiceSpec);

        /* Upgrade */
        new KafkaConfigUpgrade(schedulerBuilder.getServiceSpec(), schedulerFlags);
        Persister stateStorePersister = CuratorPersister.newBuilder(schedulerBuilder.getServiceSpec()).build();
        if (schedulerFlags.isStateCacheEnabled()) {
            stateStorePersister = new PersisterCache(stateStorePersister);
        }
        FilterStateStore stateStore = new FilterStateStore(stateStorePersister);
        stateStore.setIgnoreFilter(RegexMatcher.create("broker-[0-9]*"));
        schedulerBuilder.setStateStore(stateStore);
        /* Upgrade */

        schedulerBuilder.setEndpointProducer("zookeeper", EndpointProducer.constant(
                schedulerBuilder.getServiceSpec().getZookeeperConnection() +
                        DcosConstants.SERVICE_ROOT_PATH_PREFIX + schedulerBuilder.getServiceSpec().getName()));

        schedulerBuilder.setCustomResources(
                getResources(
                        schedulerBuilder.getServiceSpec().getZookeeperConnection(),
                        schedulerBuilder.getServiceSpec().getName()));
        initService(schedulerBuilder);
    }

    private Collection<Object> getResources(String zookeeperConnection, String serviceName) {
        KafkaZKClient kafkaZKClient = new KafkaZKClient(
                zookeeperConnection,
                DcosConstants.SERVICE_ROOT_PATH_PREFIX + serviceName);

        final Collection<Object> apiResources = new ArrayList<>();
        apiResources.add(new BrokerResource(kafkaZKClient));
        apiResources.add(new TopicResource(
                new CmdExecutor(kafkaZKClient, System.getenv("KAFKA_VERSION_PATH")),
                kafkaZKClient));

        return apiResources;
    }
}
