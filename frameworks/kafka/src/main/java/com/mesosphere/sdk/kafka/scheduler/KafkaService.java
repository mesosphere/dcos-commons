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
import com.mesosphere.sdk.offer.TaskUtils;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                            "kafka-broker-data")
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

            List<Protos.TaskInfo> taskInfoList = new ArrayList<>();
            Map<String, Protos.TaskStatus> taskStatusMap = new HashMap<>();

            LOGGER.info("CHECKING TASKS NOW !!!!!!!");
            List<String> taskNames2delete = new ArrayList<>();

            for (Protos.TaskInfo taskInfo : stateStore.fetchTasks()) {
                final UUID taskConfigId;

                LOGGER.info(">>>>>>>>>>>>>>>        \n\n");
                LOGGER.info("OLD TASKINFO");
                LOGGER.info(taskInfo.toString());

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

                String oldName = taskInfo.getName(); //broker-2
                Optional<Protos.TaskStatus> taskStatusOptional = stateStore.fetchStatus(oldName);

                Pattern pattern = Pattern.compile("(.*)-(\\d+)");
                Matcher matcher = pattern.matcher(oldName);
                matcher.find();
                int brokerID = Integer.parseInt(matcher.group(2));
                String newName = "kafka-" + brokerID + "-broker"; //kafka-2-broker

                Protos.TaskInfo.Builder taskInfoBuilder = taskInfo.toBuilder();
                taskInfoBuilder.setName(newName);
                taskInfoBuilder = CommonTaskUtils.setIndex(taskInfoBuilder, brokerID);
                taskInfoBuilder = CommonTaskUtils.setType(taskInfoBuilder, "kafka");
                taskInfoBuilder = TaskUtils.setGoalState(taskInfoBuilder, newTaskSpec);
                taskInfoBuilder = CommonTaskUtils.setTargetConfiguration(taskInfoBuilder, newTargetUUID);

                List<Protos.Resource> resourcesList = new ArrayList<>();
                for (Protos.Resource resource : taskInfo.getResourcesList()) {

                    if (!resource.hasDisk()){
                        resourcesList.add(resource);
                        continue;
                    }
                    LOGGER.info("OLD VALUE");
                    LOGGER.info(resource.toString());

                    //volume.toBuilder() was complaining that mode is not set, so setting everything manually
                    Protos.Resource.DiskInfo diskInfo = resource.getDisk();
                    Protos.Volume volume = diskInfo.getVolume();
                    Protos.Volume.Builder volumeBuilder = volume.toBuilder();
                    Protos.Volume newVolume = volumeBuilder.setSource(volume.getSource())
                                                    .setContainerPath("kafka-broker-data")
                                                    .setMode(volume.getMode())
                                                    .build();

                    Protos.Resource.DiskInfo.Builder newDiskInfoBuilder = diskInfo.toBuilder();
                    Protos.Resource.DiskInfo newDiskInfo = newDiskInfoBuilder.setVolume(newVolume)
                                                    .setPersistence(diskInfo.getPersistence())
                                                    .build();



                    Protos.Resource.Builder resourceBuilder = resource.toBuilder();
                    Protos.Resource newResource = resourceBuilder.setDisk(newDiskInfo)
                                                    .setName(resource.getName())
                                                    .setReservation(resource.getReservation())
                                                    .setRole(resource.getRole())
                                                    .setScalar(resource.getScalar())
                                                    .build();

                    LOGGER.info("NEW VALUE");
                    LOGGER.info(newResource.toString());

                    resourcesList.add(newResource);
                }
                taskInfoBuilder.clearResources();
                taskInfoBuilder.addAllResources(resourcesList);

                Protos.TaskInfo newTaskInfo = taskInfoBuilder.build();

                LOGGER.info("NEW TASKINFO");
                LOGGER.info(newTaskInfo.toString());

                taskInfoList.add(newTaskInfo);

                taskStatusMap.put(newName, taskStatusOptional.get());
                taskNames2delete.add(oldName);
            }
            LOGGER.info("old tasks: " + stateStore.fetchTasks());
            LOGGER.info("new tasks: " + taskInfoList.toString());
            LOGGER.info("new status map:" + taskStatusMap.toString());
            LOGGER.info("to delete:" + taskNames2delete.toString());


            stateStore.storeTasks(taskInfoList);


            //stateStore’s storeStatus is getting task name from task id’s prefix.
            // Not searching tasks to see who has the same task id

            for (Map.Entry<String, Protos.TaskStatus> entry: taskStatusMap.entrySet()) {
                stateStore.storeStatus(entry.getKey(), entry.getValue());
            }

            /* I dont wanna delete but cleanup looks for a matching serviceSpec then breaks since it cant read old one*/
            for (String oldName : taskNames2delete) {
                stateStore.clearTask(oldName);
            }

            newConfigStore.setTargetConfig(newTargetUUID);


            LOGGER.info("\n=====================================================\n"
                    + "         UPGRADE completed !!!\n"
                    + "=====================================================\n");

        } catch (RuntimeException e) {
            LOGGER.error("runtime error in upgrade: ", e.getMessage(), e);
            while (true) {
                Thread.sleep(100);
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            LOGGER.error("error in upgrade: ", e.getMessage(), e);
            while (true) {
                Thread.sleep(100);
            }
        }

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
