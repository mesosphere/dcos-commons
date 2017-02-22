package com.mesosphere.sdk.kafka.scheduler;

import com.google.common.collect.ImmutableMap;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.curator.CuratorConfigStore;
import com.mesosphere.sdk.kafka.api.BrokerController;
import com.mesosphere.sdk.kafka.api.KafkaZKClient;
import com.mesosphere.sdk.kafka.api.TopicController;
import com.mesosphere.sdk.kafka.cmd.CmdExecutor;
import com.mesosphere.sdk.kafka.upgrade.old.KafkaSchedulerConfiguration;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawPort;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawVip;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
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

        ServiceSpec serviceSpec = schedulerBuilder.getServiceSpec();
        StateStore stateStore = schedulerBuilder.getStateStore();

        /* We assume that old and the new configs have same names. I need to know the name to access zk */
        ConfigStore<KafkaSchedulerConfiguration> oldConfigStore = new CuratorConfigStore<>(
                                                           KafkaSchedulerConfiguration.getFactoryInstance(),
                                                           serviceSpec.getName(), serviceSpec.getZookeeperConnection());

        ConfigStore<ServiceSpec> newConfigStore  =  DefaultScheduler.createConfigStore(
                                                                    serviceSpec,
                                                                    serviceSpec.getZookeeperConnection());

        KafkaSchedulerConfiguration kafkaSchedulerConfiguration = oldConfigStore.fetch(oldConfigStore.getTargetConfig();

        ResourceSet newResourceSet = DefaultResourceSet.newBuilder()
                                        .addVolume(kafkaSchedulerConfiguration.getBrokerConfiguration().getDiskType(),
                                                    kafkaSchedulerConfiguration.getBrokerConfiguration().getDisk(),
                                                    "kafka-volume-random")
                                        .cpus(kafkaSchedulerConfiguration.getBrokerConfiguration().getCpus())
                                        .memory(kafkaSchedulerConfiguration.getBrokerConfiguration().getMem())
                                        .addPorts(
                                                new ImmutableMap("broker",
                                                        new RawPort(
                                                                kafkaSchedulerConfiguration.getBrokerConfiguration()
                                                                        .getPort().intValue(),
                                                                null,
                                                                new RawVip(null,null,null,false)))
                                        .build();

        TaskSpec newTaskSpec = DefaultTaskSpec.newBuilder()
                                .name("broker")
                                .goalState(GoalState.RUNNING)
                                .resourceSet(newResourceSet)
                                .build();

        PodSpec newPodSpec  = DefaultPodSpec.newBuilder()
                .user(kafkaSchedulerConfiguration.getServiceConfiguration().getUser())
                .count(kafkaSchedulerConfiguration.getServiceConfiguration().getCount())
                .type("kafka")
                .addTask(newTaskSpec)
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                            .name(kafkaSchedulerConfiguration.getServiceConfiguration().getName())
                            .principal(kafkaSchedulerConfiguration.getServiceConfiguration().getPrincipal())
                            .role(kafkaSchedulerConfiguration.getServiceConfiguration().getRole())
                            .zookeeperConnection(serviceSpec.getZookeeperConnection())
                            .apiPort(serviceSpec.getApiPort())
                            .addPod(newPodSpec)
                            .build();


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
