package com.mesosphere.sdk.offer;

import org.apache.mesos.Executor;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class encapsulates common methods for scanning collections of Resources.
 */
public class ResourceCollectionUtils {

    /**
     * Returns a list of all the resources associated with one or more tasks, including {@link Executor} resources.
     * The returned resources may contain duplicates if multiple tasks have copies of the same resource.
     */
    public static List<Protos.Resource> getAllResources(Collection<TaskInfo> taskInfos) {
        return taskInfos.stream()
                .map(ResourceCollectionUtils::getAllResources)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * Returns a list of all the resources associated with a task, including {@link Executor} resources.
     *
     * @param taskInfo The {@link Protos.TaskInfo} containing the {@link Protos.Resource}.
     * @return a list of {@link Protos.Resource}s.
     */
    public static List<Protos.Resource> getAllResources(Protos.TaskInfo taskInfo) {
        List<Resource> resources = new ArrayList<>();
        // Get all resources from both the task level and the executor level
        resources.addAll(taskInfo.getResourcesList());
        if (taskInfo.hasExecutor()) {
            resources.addAll(taskInfo.getExecutor().getResourcesList());
        }
        return resources;
    }

    /**
     * Returns a list of unique resource IDs associated with {@link Resource}s.
     *
     * @param resources Collection of resources from which to extract the unique resource IDs
     * @return List of unique resource IDs
     */
    public static List<String> getResourceIds(Collection<Resource> resources) {
        return resources.stream()
                .map(ResourceCollectionUtils::getResourceId)
                .filter(resourceId -> resourceId.isPresent())
                .map(resourceId -> resourceId.get())
                .distinct()
                .collect(Collectors.toList());
    }

    public static Optional<String> getResourceId(Resource resource) {
        return resource.getReservation().getLabels().getLabelsList().stream()
                .filter(label -> label.getKey().equals(MesosResource.RESOURCE_ID_KEY))
                .map(label -> label.getValue())
                .findFirst();
    }
}
