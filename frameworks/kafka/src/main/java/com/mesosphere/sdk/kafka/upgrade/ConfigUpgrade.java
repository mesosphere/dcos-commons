package com.mesosphere.sdk.kafka.upgrade;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.curator.CuratorConfigStore;
import com.mesosphere.sdk.kafka.upgrade.old.KafkaSchedulerConfiguration;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Create an intermediate Configuration with ServiceSpec type.
 * Create new TaskInfo and TaskStatus with new format and new name.
 */
public class ConfigUpgrade {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ConfigUpgrade.class);

    private final StateStore stateStore;
    private UUID oldTargetId;
    private UUID newTargetId;
    private ConfigStore<ServiceSpec> configStore;

    public ConfigUpgrade(ServiceSpec serviceSpec, StateStore stateStore) throws Exception {
        this.stateStore = stateStore;
        startUpgrade(serviceSpec);
    }

    private void startUpgrade(ServiceSpec serviceSpec) throws Exception{

        Optional<KafkaSchedulerConfiguration> kafkaSchedulerConfiguration = getOldConfiguration(serviceSpec);
        if (!kafkaSchedulerConfiguration.isPresent()){
            LOGGER.info("Ignoring Kafka Configuration Upgrade : Can not get target configuration or it is not" +
                    "in KafkaSchedulerConfiguration format");
            return;
        }
        this.configStore  = createConfigStore(serviceSpec);

        if (!verifyOldTasks(oldTargetId)){
            throw new ConfigUpgradeException("Aborting Kafka Configuration Upgrade !!!");
        }

        ServiceSpec newServiceSpec = generateServiceSpec(kafkaSchedulerConfiguration.get(), serviceSpec);

        LOGGER.info("Kafka Configuration Upgrade started.");

        this.newTargetId = configStore.store(newServiceSpec);

        Collection<Protos.TaskInfo> newTaskInfos;
        Map<String, Protos.TaskStatus> newStatusMap;

        Collection<String> oldTaskNames = stateStore.fetchTaskNames();
        newTaskInfos = generateTaskInfos(oldTaskNames, newServiceSpec);
        newStatusMap = generateStatusMap(oldTaskNames);

        stateStore.storeTasks(newTaskInfos);

        for (Map.Entry<String, Protos.TaskStatus> entry: newStatusMap.entrySet()) {
            stateStore.storeStatus(entry.getKey(), entry.getValue());
        }

        if (cleanup()) {
            for (String oldName : oldTaskNames) {
                stateStore.clearTask(oldName);
            }
        }

        configStore.setTargetConfig(newTargetId);
        LOGGER.info("Kafka Configuration Upgrade complete.");
    }

    private boolean verifyOldTasks(UUID oldTargetId) {
        for (Protos.TaskInfo taskInfo : stateStore.fetchTasks()) {
            final UUID taskConfigId;

            try {
                taskConfigId = CommonTaskUtils.getTargetConfiguration(taskInfo);
            } catch (TaskException e) {
                LOGGER.error(String.format("Unable to extract configuration id from task %s: %s",
                        taskInfo.getName(), TextFormat.shortDebugString(taskInfo)), e);
                return false;
            }

            if (!taskConfigId.equals(oldTargetId)) {
                LOGGER.error("Task {} target configuration does not match with target : {}",
                        taskInfo.getName(), oldTargetId);
                return false;
            }

            Optional<Protos.TaskStatus> optionalStatus = stateStore.fetchStatus(taskInfo.getName());
            if (!optionalStatus.isPresent()){
                LOGGER.error("Unable to fetch status for task  {} ", taskInfo.getName());
                return false;
            }

            if (!(optionalStatus.get().getState()).equals(Protos.TaskState.TASK_RUNNING)){
                LOGGER.error("Task {} is not in RUNNING state. Task status: {} ",
                        taskInfo.getName(), optionalStatus.get());
                return false;
            }
        }
        return true;
    }

    private ConfigStore<ServiceSpec> createConfigStore(ServiceSpec serviceSpec) throws ConfigStoreException {
       return DefaultScheduler.createConfigStore(
                    serviceSpec,
                    serviceSpec.getZookeeperConnection());
    }

    private Optional<KafkaSchedulerConfiguration> getOldConfiguration(ServiceSpec serviceSpec) {
        KafkaSchedulerConfiguration kafkaSchedulerConfiguration;

        /* We assume that old and new configurations both have same name. */
        ConfigStore<KafkaSchedulerConfiguration> oldConfigStore = new CuratorConfigStore<>(
                KafkaSchedulerConfiguration.getFactoryInstance(),
                serviceSpec.getName(), serviceSpec.getZookeeperConnection());

        try {
            this.oldTargetId = oldConfigStore.getTargetConfig();
             kafkaSchedulerConfiguration = oldConfigStore.fetch(oldTargetId);

            LOGGER.info("name: " + kafkaSchedulerConfiguration.getServiceConfiguration().getName());
            LOGGER.info("role: " + kafkaSchedulerConfiguration.getServiceConfiguration().getRole());
            LOGGER.info("principle: " + kafkaSchedulerConfiguration.getServiceConfiguration().getPrincipal());
            LOGGER.info("user: " + kafkaSchedulerConfiguration.getServiceConfiguration().getUser());
            LOGGER.info("broker count: " + kafkaSchedulerConfiguration.getServiceConfiguration().getCount());
            LOGGER.info("disk type: " + kafkaSchedulerConfiguration.getBrokerConfiguration().getDiskType());
            LOGGER.info("disk size: " + kafkaSchedulerConfiguration.getBrokerConfiguration().getDisk());

        } catch (Exception e) {
            LOGGER.error("Error while retrieving old Configuration to upgrade", e);
            return Optional.empty();
        }
        return Optional.of(kafkaSchedulerConfiguration);
    }

    private ServiceSpec generateServiceSpec(KafkaSchedulerConfiguration kafkaSchedulerConfiguration,
                                            ServiceSpec serviceSpec) {
        /* TODO(mb): why I can not create RawPort. See below
        LinkedHashMap<String, RawPort> portMap = new WriteOnceLinkedHashMap<>();
        portMap.put("broker",
                new RawPort(
                        kafkaSchedulerConfiguration.getBrokerConfiguration()
                                .getPort().intValue(),
                        null, null));
        */
        ResourceSet newResourceSet = DefaultResourceSet.newBuilder(
                kafkaSchedulerConfiguration.getServiceConfiguration().getRole(),
                kafkaSchedulerConfiguration.getServiceConfiguration().getPrincipal())
                .id("broker-resource-set")
                .addVolume(kafkaSchedulerConfiguration.getBrokerConfiguration().getDiskType(),
                        kafkaSchedulerConfiguration.getBrokerConfiguration().getDisk(),
                        "kafka-broker-data")
                .cpus(kafkaSchedulerConfiguration.getBrokerConfiguration().getCpus())
                .memory(kafkaSchedulerConfiguration.getBrokerConfiguration().getMem())
                // TODO(mb) .addPorts(portMap)
                .build();

        TaskSpec newTaskSpec = DefaultTaskSpec.newBuilder()
                .name("broker")
                .goalState(GoalState.RUNNING)
                //  No commandSpec empty so both Specs will not be equal.
                .resourceSet(newResourceSet)
                .build();

        PodSpec newPodSpec = DefaultPodSpec.newBuilder()
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
        return newServiceSpec;
    }

    private Collection<Protos.TaskInfo> generateTaskInfos(Collection<String> oldTaskNames,
                                 ServiceSpec newServiceSpec) throws  ConfigUpgradeException {
        List<Protos.TaskInfo> taskInfoList = new ArrayList<>();

        for (String oldTaskName : oldTaskNames) {
            int brokerId = oldTaskName2BrokerId(oldTaskName);
            String newName = getNewTaskName(brokerId);

            Optional<Protos.TaskInfo> oldTaskInfo = stateStore.fetchTask(oldTaskName);
            if (!oldTaskInfo.isPresent()){
                throw new ConfigUpgradeException("Can not fetch task info " + oldTaskName);
            }

            LOGGER.info("Task {} old TaskInfo: ", oldTaskInfo.get().getName(), oldTaskInfo);

            Protos.TaskInfo.Builder taskInfoBuilder = oldTaskInfo.get().toBuilder();
            taskInfoBuilder.setName(newName);
            taskInfoBuilder = CommonTaskUtils.setIndex(taskInfoBuilder, brokerId);
            taskInfoBuilder = CommonTaskUtils.setType(taskInfoBuilder, "kafka");
            taskInfoBuilder = TaskUtils.setGoalState(taskInfoBuilder,
                    newServiceSpec.getPods().get(0).getTasks().get(0));
            taskInfoBuilder = CommonTaskUtils.setTargetConfiguration(taskInfoBuilder, newTargetId);

            List<Protos.Resource> resourcesList = new ArrayList<>();
            for (Protos.Resource resource : oldTaskInfo.get().getResourcesList()) {
                if (!resource.hasDisk()){
                    resourcesList.add(resource);
                    continue;
                }
                LOGGER.info("old disk resource : {}", resource);

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

                LOGGER.info("new disk resource : {}", newResource);

                resourcesList.add(newResource);
            }
            taskInfoBuilder.clearResources();
            taskInfoBuilder.addAllResources(resourcesList);

            Protos.TaskInfo newTaskInfo = taskInfoBuilder.build();
            LOGGER.info("Task {} new TaskInfo: ", newTaskInfo.getName(), newTaskInfo);
            taskInfoList.add(newTaskInfo);
        }
        return taskInfoList;
    }

    private Map<String, Protos.TaskStatus> generateStatusMap(Collection<String> oldTaskNames)
            throws ConfigUpgradeException{
        Map<String, Protos.TaskStatus> taskStatusMap = new HashMap<>();

        for (String oldTaskName : oldTaskNames) {
            int brokerId = oldTaskName2BrokerId(oldTaskName);
            String newName = getNewTaskName(brokerId);
            Optional<Protos.TaskStatus> optionalStatus = stateStore.fetchStatus(oldTaskName);
            if (!optionalStatus.isPresent()){
                throw new ConfigUpgradeException("Can not fetch status for Task " + oldTaskName);
            }
            taskStatusMap.put(newName, optionalStatus.get());
        }
        return taskStatusMap;
    }

    private int oldTaskName2BrokerId(String taskName){
        Pattern pattern = Pattern.compile("(.*)-(\\d+)");
        Matcher matcher = pattern.matcher(taskName);
        matcher.find();
        return Integer.parseInt(matcher.group(2));
    }

    private String getNewTaskName(int brokerID){
        return "kafka-" + brokerID + "-broker"; //kafka-2-broker
    }

    public static boolean enabled(){
        if (System.getenv("CONFIG_UPGRADE_ENABLE") != null) {
            return true;
        }
        return false;
    }

    public static boolean cleanup(){
        if (System.getenv("CONFIG_UPGRADE_CLEANUP") != null) {
            return true;
        }
        return false;
    }

}
