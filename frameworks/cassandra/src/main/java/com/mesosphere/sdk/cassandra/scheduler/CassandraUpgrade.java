package com.mesosphere.sdk.cassandra.scheduler;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class manipulates Cassandra ServiceSpec and TaskInfos to move from volumes being on the
 * Tasks to being on the Executors.
 */
public class CassandraUpgrade {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraUpgrade.class);

    public static void upgradeProtobufs(UUID newTargetId, ServiceSpec serviceSpec, StateStore stateStore) {

        Collection<Protos.TaskInfo> taskInfos = stateStore.fetchTasks();

        LOGGER.info("Original taskInfos");
        taskInfos.forEach(taskInfo -> LOGGER.info(TextFormat.shortDebugString(taskInfo)));

        // Update target ids
        taskInfos = setTargetId(newTargetId, taskInfos);
        LOGGER.info("Updated taskInfos with new target ids.");
        taskInfos.forEach(taskInfo -> LOGGER.info(TextFormat.shortDebugString(taskInfo)));

        // All tasks in the service
        Map<String, Protos.TaskInfo> allTasks = stateStore.fetchTasks().stream()
                .collect(Collectors.toMap(Protos.TaskInfo::getName, Function.identity()));

        for (PodInstance podInstance : getPodInstances(serviceSpec.getPods().get(0))) {
            // Tasks for this pod
            List<Protos.TaskInfo> thisPodTasks =
                    TaskUtils.getTaskNames(podInstance).stream()
                            .map(taskName -> allTasks.get(taskName))
                            .filter(taskInfo -> taskInfo != null)
                            .collect(Collectors.toList());

            // Move disk resources to the executor when present
            List<Protos.TaskInfo> updatedTaskInfos = thisPodTasks.stream()
                    .map(taskInfo -> moveDiskResourceToExecutor(taskInfo))
                    .collect(Collectors.toList());

            // Find an executor that now has resources on it
            Protos.ExecutorInfo executorInfo = updatedTaskInfos.stream()
                    .map(taskInfo -> taskInfo.getExecutor())
                    .filter(execInfo -> execInfo.getResourcesCount() > 0)
                    .findFirst()
                    .get();

            // Apply that executor to all tasks in this pod
            updatedTaskInfos = updatedTaskInfos.stream()
                    .map(taskInfo -> taskInfo.toBuilder().setExecutor(executorInfo).build())
                    .collect(Collectors.toList());

            LOGGER.info("Updated taskInfos moving disk resources to the executor.");
            updatedTaskInfos.forEach(taskInfo -> LOGGER.info(TextFormat.shortDebugString(taskInfo)));

            stateStore.storeTasks(updatedTaskInfos);
        }

    }


    // Upgrade Specs


    public static boolean needsUpgrade(ServiceSpec serviceSpec) {
        // If the Cassandra volume isn't at the pod level it needs to be.
        return serviceSpec.getPods().stream().anyMatch(podSpec -> podSpec.getVolumes().isEmpty());
    }

    static ServiceSpec upgradeServiceSpec(ServiceSpec serviceSpec) {
        List<PodSpec> podSpecs = serviceSpec.getPods().stream()
                .map(podSpec -> upgradePodSpec(podSpec))
                .collect(Collectors.toList());

        return DefaultServiceSpec.newBuilder(serviceSpec)
                .pods(podSpecs)
                .build();
    }

    static PodSpec upgradePodSpec(PodSpec podSpec) {
        VolumeSpec volumeSpec = getVolumeSpec(podSpec);
        List<TaskSpec> taskSpecs = podSpec.getTasks().stream()
                .map(taskSpec -> upgradeTaskSpec(taskSpec))
                .collect(Collectors.toList());

        return DefaultPodSpec.newBuilder(podSpec)
                .tasks(taskSpecs)
                .volumes(Arrays.asList(volumeSpec))
                .build();
    }

    static TaskSpec upgradeTaskSpec(TaskSpec taskSpec) {
        ResourceSet resourceSet = upgradeResourceSet(taskSpec.getResourceSet());
        return DefaultTaskSpec.newBuilder(taskSpec)
                .resourceSet(resourceSet)
                .build();
    }

    static ResourceSet upgradeResourceSet(ResourceSet resourceSet) {
        if (resourceSet.getVolumes().isEmpty()) {
            return resourceSet;
        }

        return DefaultResourceSet.newBuilder((DefaultResourceSet) resourceSet).volumes(Collections.emptyList()).build();
    }

    private static VolumeSpec getVolumeSpec(PodSpec podSpec) {
        return getServerResourceSet(podSpec).getVolumes().stream().findFirst().get();
    }

    private static ResourceSet getServerResourceSet(PodSpec podSpec) {
        return getServerTaskSpec(podSpec).getResourceSet();
    }

    private static TaskSpec getServerTaskSpec(PodSpec podSpec) {
        return podSpec.getTasks().stream()
                .filter(taskSpec -> taskSpec.getName().equals("server"))
                .findFirst()
                .get();
    }


    // Upgrade protobufs


    public static Collection<Protos.TaskInfo> setTargetId(UUID newTargetId, Collection<Protos.TaskInfo> taskInfos) {

        Collection<Protos.TaskInfo> updatedTaskInfos = new ArrayList<>();
        for (Protos.TaskInfo taskInfo : taskInfos) {
            Protos.TaskInfo.Builder builder = taskInfo.toBuilder();
            updatedTaskInfos.add(
                    builder.setLabels(new TaskLabelWriter(taskInfo.toBuilder())
                            .setTargetConfiguration(newTargetId)
                            .toProto())
                            .build());
        }

        return updatedTaskInfos;
    }

    static Protos.TaskInfo moveDiskResourceToExecutor(Protos.TaskInfo taskInfo)  {
        List<Protos.Resource> diskResources = getDiskResources(taskInfo);

        Protos.ExecutorInfo executorInfo = taskInfo.getExecutor().toBuilder()
                .addAllResources(diskResources)
                .build();

        return taskInfo.toBuilder()
                .clearResources()
                .addAllResources(getNonDiskResources(taskInfo))
                .setExecutor(executorInfo)
                .build();
    }

    private static List<Protos.Resource> getDiskResources(Protos.TaskInfo taskInfo) {
        return taskInfo.getResourcesList().stream()
                .filter(resource -> resource.getName().equals("disk"))
                .collect(Collectors.toList());
    }

    private static List<Protos.Resource> getNonDiskResources(Protos.TaskInfo taskInfo) {
        return taskInfo.getResourcesList().stream()
                .filter(resource -> !resource.getName().equals("disk"))
                .collect(Collectors.toList());
    }


    // Utils


    private static List<PodInstance> getPodInstances(PodSpec podSpec) {
        List<PodInstance> podInstances = new ArrayList<>();
        for (int i = 0; i < podSpec.getCount(); i++) {
            podInstances.add(new DefaultPodInstance(podSpec, i));
        }

        return podInstances;
    }

}
