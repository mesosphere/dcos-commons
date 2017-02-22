package com.mesosphere.sdk.kafka.scheduler;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.curator.CuratorConfigStore;
import com.mesosphere.sdk.kafka.api.BrokerController;
import com.mesosphere.sdk.kafka.api.KafkaZKClient;
import com.mesosphere.sdk.kafka.api.TopicController;
import com.mesosphere.sdk.kafka.cmd.CmdExecutor;
import com.mesosphere.sdk.kafka.upgrade.old.KafkaSchedulerConfiguration;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.*;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.StorageError;
import org.apache.mesos.Protos;
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

        final ServiceSpec serviceSpec = schedulerBuilder.getServiceSpec();
        final StateStore stateStore = schedulerBuilder.getStateStore();


        try {

            //TODO(fails): if upgrade fails do not retry or do something to revert back
            LOGGER.info("\n=====================================================\n"
                    + "                     UPGRADE started !!!\n"
                    + "=====================================================\n");

        /* We assume that old and the new configs have same names. I need to know the name to access zk */
            ConfigStore<KafkaSchedulerConfiguration> oldConfigStore = new CuratorConfigStore<>(
                    KafkaSchedulerConfiguration.getFactoryInstance(),
                    serviceSpec.getName(), serviceSpec.getZookeeperConnection());


            final UUID oldTargetUUID = oldConfigStore.getTargetConfig();
            final KafkaSchedulerConfiguration kafkaSchedulerConfiguration = oldConfigStore.fetch(oldTargetUUID);


        /* TODO(port): take care RawPort */
        /*
        LinkedHashMap<String, RawPort> portMap = new WriteOnceLinkedHashMap<>();
        portMap.put("broker",
                new RawPort(
                        kafkaSchedulerConfiguration.getBrokerConfiguration()
                                .getPort().intValue(),
                        null, null));
        */

            LOGGER.info("name: " + kafkaSchedulerConfiguration.getServiceConfiguration().getName());
            LOGGER.info("role: " + kafkaSchedulerConfiguration.getServiceConfiguration().getRole());
            LOGGER.info("principle: " + kafkaSchedulerConfiguration.getServiceConfiguration().getPrincipal());
            LOGGER.info("user: " + kafkaSchedulerConfiguration.getServiceConfiguration().getUser());
            LOGGER.info("broker count: " + kafkaSchedulerConfiguration.getServiceConfiguration().getCount());
            LOGGER.info("disk type: " + kafkaSchedulerConfiguration.getBrokerConfiguration().getDiskType());
            LOGGER.info("disk size: " + kafkaSchedulerConfiguration.getBrokerConfiguration().getDisk());


            //TODO(fail) if you get an error exit

            ResourceSet newResourceSet = DefaultResourceSet.newBuilder(
                    kafkaSchedulerConfiguration.getServiceConfiguration().getRole(),
                    kafkaSchedulerConfiguration.getServiceConfiguration().getPrincipal())
                    .id("broker-resource-set")
                    .addVolume(kafkaSchedulerConfiguration.getBrokerConfiguration().getDiskType(),
                            kafkaSchedulerConfiguration.getBrokerConfiguration().getDisk(),
                            "kafka-volume-random")
                    .cpus(kafkaSchedulerConfiguration.getBrokerConfiguration().getCpus())
                    .memory(kafkaSchedulerConfiguration.getBrokerConfiguration().getMem())
                    // TODO(port) .addPorts(portMap)
                    .build();

            TaskSpec newTaskSpec = DefaultTaskSpec.newBuilder()
                    .name("broker")
                    .goalState(GoalState.RUNNING)
                    .commandSpec(serviceSpec.getPods().get(0).getTasks().get(0).getCommand().get())
                    .resourceSet(newResourceSet)
                    .build();

            PodSpec newPodSpec = DefaultPodSpec.newBuilder()
                    .user(kafkaSchedulerConfiguration.getServiceConfiguration().getUser())
                    .count(kafkaSchedulerConfiguration.getServiceConfiguration().getCount())
                    .type("kafka")
                    .addTask(newTaskSpec)
                    .build();

            final ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                    .name(kafkaSchedulerConfiguration.getServiceConfiguration().getName())
                    .principal(kafkaSchedulerConfiguration.getServiceConfiguration().getPrincipal())
                    .role(kafkaSchedulerConfiguration.getServiceConfiguration().getRole())
                    .zookeeperConnection(serviceSpec.getZookeeperConnection())
                    .apiPort(serviceSpec.getApiPort())
                    .addPod(newPodSpec)
                    .build();

            final ConfigStore<ServiceSpec> newConfigStore = DefaultScheduler.createConfigStore(
                    serviceSpec,
                    serviceSpec.getZookeeperConnection());

            final UUID newTargetUUID = newConfigStore.store(newServiceSpec);
            newConfigStore.setTargetConfig(newTargetUUID);

            List<Protos.TaskInfo> taskInfoList = new ArrayList<>();
            List<Protos.TaskStatus> taskStatusList = new ArrayList<>();

            List<String> tastNames2delete = new ArrayList<>();
            for (Protos.TaskInfo taskInfo : stateStore.fetchTasks()) {
                final UUID taskConfigId;
                try {
                    taskConfigId = CommonTaskUtils.getTargetConfiguration(taskInfo);
                } catch (TaskException e) {
                    LOGGER.warn(String.format("Unable to extract configuration ID from task %s: %s",
                            taskInfo.getName(), TextFormat.shortDebugString(taskInfo)), e);
                    continue;
                }

                if (!taskConfigId.equals(oldTargetUUID)) {
                    LOGGER.info("Task {} target configuration does not match with the target : {}  Aborting ... ",
                            taskInfo.getName(), oldTargetUUID);
                    //TODO(v2) make sure old config is removed when you increase the count
                    throw new ConfigStoreException(StorageError.Reason.UNKNOWN,
                            "Make sure all tasks reached target before the Upgrade");
                }
                int i = 0;
                String oldName = taskInfo.getName();
                Optional<Protos.TaskStatus> taskStatusOptional = stateStore.fetchStatus(oldName);
                String newName = "kafka-" + i + "-broker";
                i++; //TODO(v2) fix this later
                taskInfoList.add(CommonTaskUtils.setTargetConfiguration(taskInfo.toBuilder().setName(newName),
                        newTargetUUID).build());
                taskStatusList.add(taskStatusOptional.get());
                tastNames2delete.add(oldName);
            }
            stateStore.storeTasks(taskInfoList);

            for (String oldName : tastNames2delete) {
                stateStore.clearTask(oldName);
            }
            for (Protos.TaskStatus status : taskStatusList) {
                stateStore.storeStatus(status);
            }

            LOGGER.info("\n=====================================================\n"
                    + "         UPGRADE completed !!!\n"
                    + "=====================================================\n");

        } catch (RuntimeException e) {
            LOGGER.error(e.getMessage());
            LOGGER.error("error in upgrade: ", e.getStackTrace());

        }
        while (true) {
                Thread.sleep(100000);
        }
        //initService(schedulerBuilder);
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
