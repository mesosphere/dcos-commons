package com.mesosphere.sdk.kafka.upgrade;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.curator.CuratorPersister;
import com.mesosphere.sdk.kafka.upgrade.old.KafkaSchedulerConfiguration;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelReader;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.DefaultConfigStore;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KafkaConfigUpgrade migrates configuration from old config format to new ServiceSpec format.
 * Creates an intermediate configuration in ServiceSpec format.
 * Also adds new TaskInfo and TaskStatus with new format and new task names.
 */
public class KafkaConfigUpgrade {
    protected static final Logger LOGGER = LoggerFactory.getLogger(KafkaConfigUpgrade.class);

    /**
     *  KafkaConfigUpgrade Exception.
     */
    public static class KafkaConfigUpgradeException extends IOException {
        public KafkaConfigUpgradeException(String message){
            super(message);
        }
    }

    private final UpdateStateStore stateStore;
    private UUID oldTargetId;
    private UUID newTargetId;
    private ConfigStore<ServiceSpec> configStore;
    private String newPath;

    /**
     *  KafkaConfigUpgrade.
     */
    public KafkaConfigUpgrade(ServiceSpec serviceSpec, SchedulerFlags schedulerFlags) throws Exception {
        this.stateStore = new UpdateStateStore(CuratorPersister.newBuilder(serviceSpec).build());

        // if framework_id exist and not disabled
        if (!KafkaConfigUpgrade.disabled() && !runningFirstTime(stateStore)) {
            startUpgrade(serviceSpec, schedulerFlags);
        }
    }

    private void startUpgrade(ServiceSpec serviceSpec, SchedulerFlags schedulerFlags) throws Exception{
        Optional<KafkaSchedulerConfiguration> kafkaSchedulerConfiguration = getOldConfiguration(serviceSpec);
        if (!kafkaSchedulerConfiguration.isPresent()){
            LOGGER.info("\n ---------------------------------------------------- \n " +
                    "          Ignoring Kafka Configuration Upgrade. \n" +
                    " ---------------------------------------------------- ");
            LOGGER.error(" Can not get target configuration or it is not " +
                    "in KafkaSchedulerConfiguration format");
            return;
        }
        this.configStore = DefaultScheduler.createConfigStore(serviceSpec, Collections.emptyList());

        if (!verifyOldTasks(oldTargetId)){
            throw new KafkaConfigUpgradeException("Aborting Kafka Configuration Upgrade !!!");
        }
        verifyNewSpec(serviceSpec);
        ServiceSpec newServiceSpec =
                generateServiceSpec(kafkaSchedulerConfiguration.get(), serviceSpec, schedulerFlags);

        LOGGER.info("\n ---------------------------------------------------- \n " +
                    "          Kafka Configuration Upgrade started. \n" +
                    " ---------------------------------------------------- ");

        this.newTargetId = configStore.store(newServiceSpec);

        Collection<Protos.TaskInfo> newTaskInfos;
        Map<String, Protos.TaskStatus> newStatusMap;

        Collection<String> oldTaskNames = getOldTaskNames(stateStore.fetchTaskNames());

        newTaskInfos = generateTaskInfos(oldTaskNames, newServiceSpec);
        newStatusMap = generateStatusMap(oldTaskNames, newServiceSpec);

        stateStore.storeTasks(newTaskInfos);

        for (Map.Entry<String, Protos.TaskStatus> entry: newStatusMap.entrySet()) {
            stateStore.storeStatus(entry.getKey(), entry.getValue());
        }

        configStore.setTargetConfig(newTargetId);

        if (cleanup()) {
            for (String oldName : oldTaskNames) {
                stateStore.clearTask(oldName);
            }
            configStore.clear(oldTargetId);
        }

        LOGGER.info("\n ---------------------------------------------------- \n " +
                    "          Kafka Configuration Upgrade complete. \n" +
                    " ---------------------------------------------------- ");
    }

    private boolean verifyOldTasks(UUID oldTargetId) {
        for (Protos.TaskInfo taskInfo : stateStore.fetchTasks()) {
            final UUID taskConfigId;

            if (getOldTaskNames(ImmutableList.of(taskInfo.getName())).size() <= 0) {
                LOGGER.info("Skipping task {} for verification.", taskInfo.getName());
                continue;
            }
            try {
                taskConfigId = new SchedulerLabelReader(taskInfo).getTargetConfiguration();
            } catch (TaskException e) {
                LOGGER.error("Unable to extract configuration id from task {}: {}  error: {}",
                        taskInfo.getName(), TextFormat.shortDebugString(taskInfo), e.getMessage());
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

    private  void verifyNewSpec(ServiceSpec serviceSpec) throws KafkaConfigUpgradeException{
        // This is a custom Upgrade: I do not know how to proceed if there
        //                  are multiple tasks, or multiple volumes, or multiple pods,
        //                  or there is no volume, etc.!
        if (serviceSpec.getPods().size() != 1
                || serviceSpec.getPods().get(0).getTasks().size() != 1
                || serviceSpec.getPods().get(0).getTasks().get(0).getResourceSet().getVolumes().size() != 1) {
            LOGGER.error("New config {}: number of pods {}, number of Tasks in first pod {}, " +
                            "number of Volumes in first task{} ",
                    serviceSpec.getName(),
                    serviceSpec.getPods().size(),
                    serviceSpec.getPods().get(0).getTasks().size(),
                    serviceSpec.getPods().get(0).getTasks().get(0).getResourceSet().getVolumes().size());
            throw new KafkaConfigUpgradeException("New configuration is not compatible. I can not upgrade!");
        }
        // kafka-broker-data
        this.newPath = serviceSpec.getPods().get(0).getTasks().get(0)
                .getResourceSet().getVolumes().stream().findFirst().get().getContainerPath();
    }

    private Optional<KafkaSchedulerConfiguration> getOldConfiguration(ServiceSpec serviceSpec) {
        KafkaSchedulerConfiguration kafkaSchedulerConfiguration;

        /* We assume that old and new configurations both have same name. */
        ConfigStore<KafkaSchedulerConfiguration> oldConfigStore = new DefaultConfigStore<>(
                KafkaSchedulerConfiguration.getFactoryInstance(), CuratorPersister.newBuilder(serviceSpec).build());

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
            LOGGER.error("Error while retrieving old Configuration to upgrade", e.getMessage());
            return Optional.empty();
        }
        return Optional.of(kafkaSchedulerConfiguration);
    }

    private ServiceSpec generateServiceSpec(KafkaSchedulerConfiguration kafkaSchedulerConfiguration,
                                            ServiceSpec serviceSpec,
                                            SchedulerFlags schedulerFlags) {
        /* TODO(mb): why I can not create RawPort. See below:
        LinkedHashMap<String, RawPort> portMap = new WriteOnceLinkedHashMap<>();
        portMap.put("broker",
                new RawPort(
                        kafkaSchedulerConfiguration.getBrokerConfiguration()
                                .getPort().intValue(),
                        null, null));
        It will use previously reserved ports, since there are existing tasks,
            no need to give ports to intermediate Spec */
        ResourceSet newResourceSet = DefaultResourceSet.newBuilder(
                kafkaSchedulerConfiguration.getServiceConfiguration().getRole(),
                kafkaSchedulerConfiguration.getServiceConfiguration().getPrincipal())
                // it does not matter what name I gave to resource set
                .id("broker-resource-set")
                .addVolume(kafkaSchedulerConfiguration.getBrokerConfiguration().getDiskType(),
                        kafkaSchedulerConfiguration.getBrokerConfiguration().getDisk(),
                        this.newPath)
                .cpus(kafkaSchedulerConfiguration.getBrokerConfiguration().getCpus())
                .memory(kafkaSchedulerConfiguration.getBrokerConfiguration().getMem())
                // TODO(mb) .addPorts(portMap)
                .build();

        TaskSpec newTaskSpec = DefaultTaskSpec.newBuilder()
                .name(serviceSpec.getPods().get(0).getTasks().get(0).getName())
                // it should be always RUNNING i.e.  .goalState(GoalState.RUNNING)
                .goalState(serviceSpec.getPods().get(0).getTasks().get(0).getGoal())
                //  No commandSpec, to guarantee Specs will not be equal.
                .resourceSet(newResourceSet)
                .build();

        PodSpec newPodSpec = DefaultPodSpec.newBuilder(schedulerFlags.getExecutorURI())
                .user(kafkaSchedulerConfiguration.getServiceConfiguration().getUser())
                .count(kafkaSchedulerConfiguration.getServiceConfiguration().getCount())
                .type(serviceSpec.getPods().get(0).getType())
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

    private boolean runningFirstTime(StateStore stateStore) {
        if (stateStore.fetchFrameworkId().isPresent()) {
            return false;
        }
        return true;
    }

    private Collection<Protos.TaskInfo> generateTaskInfos(Collection<String> oldTaskNames,
                                 ServiceSpec newServiceSpec) throws  KafkaConfigUpgradeException {
        List<Protos.TaskInfo> taskInfoList = new ArrayList<>();

        for (String oldTaskName : oldTaskNames) {
            int brokerId = oldTaskName2BrokerId(oldTaskName);
            // newServiceSpec is already verified!
            String newName = getNewTaskName(brokerId,
                    newServiceSpec.getPods().get(0).getType(),
                    newServiceSpec.getPods().get(0).getTasks().get(0).getName());

            Optional<Protos.TaskInfo> oldTaskInfo = stateStore.fetchTask(oldTaskName);
            if (!oldTaskInfo.isPresent()){
                throw new KafkaConfigUpgradeException("Can not fetch task info " + oldTaskName);
            }

            LOGGER.info("Task {} old TaskInfo: ", oldTaskInfo.get().getName(), oldTaskInfo);

            Protos.TaskInfo.Builder taskInfoBuilder = oldTaskInfo.get().toBuilder();
            taskInfoBuilder.setName(newName);
            taskInfoBuilder.setLabels(new SchedulerLabelWriter(taskInfoBuilder)
                    .setIndex(brokerId)
                    .setType("kafka")
                    .setGoalState(newServiceSpec.getPods().get(0).getTasks().get(0).getGoal())
                    .setTargetConfiguration(newTargetId)
                    .toProto());

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
                        .setContainerPath(this.newPath) //kafka-broker-data
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

    private Map<String, Protos.TaskStatus> generateStatusMap(Collection<String> oldTaskNames,
                                                             ServiceSpec newServiceSpec)
            throws KafkaConfigUpgradeException{
        Map<String, Protos.TaskStatus> taskStatusMap = new HashMap<>();

        for (String oldTaskName : oldTaskNames) {
            int brokerId = oldTaskName2BrokerId(oldTaskName);
            String newName = getNewTaskName(brokerId,
                    newServiceSpec.getPods().get(0).getType(),
                    newServiceSpec.getPods().get(0).getTasks().get(0).getName());

            Optional<Protos.TaskStatus> optionalStatus = stateStore.fetchStatus(oldTaskName);
            if (!optionalStatus.isPresent()){
                throw new KafkaConfigUpgradeException("Can not fetch status for Task " + oldTaskName);
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

    private String getNewTaskName(int brokerID, String podType, String taskName){
        return podType + "-" + brokerID + "-" + taskName; //kafka-2-broker
    }

    private Collection<String> getOldTaskNames(Collection<String> taskNames) {
        Collection<String> oldNames = new ArrayList<>();
        Pattern pattern = Pattern.compile("(.*)-(\\d+)");
        for (String taskName: taskNames) {
            Matcher matcher = pattern.matcher(taskName);
            if (matcher.find()) {
                oldNames.add(taskName);
            }
        }
        return oldNames;
    }

    public static boolean cleanup(){
        if (System.getenv("CONFIG_UPGRADE_CLEANUP") != null) {
            return true;
        }
        return false;
    }
    public static boolean disabled(){
        if (System.getenv("CONFIG_UPGRADE_DISABLE") != null) {
            return true;
        }
        return false;
    }
}
