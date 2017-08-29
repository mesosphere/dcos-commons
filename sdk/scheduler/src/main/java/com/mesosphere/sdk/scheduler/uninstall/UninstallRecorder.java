package com.mesosphere.sdk.scheduler.uninstall;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.Constants.TOMBSTONE_MARKER;

/**
 * Records to persistent storage the result of uninstalling/destroying resources.
 */
public class UninstallRecorder implements OperationRecorder {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StateStore stateStore;
    private final Collection<Step> resourceSteps;

    UninstallRecorder(StateStore stateStore, Collection<Step> resourceSteps) {
        this.stateStore = stateStore;
        this.resourceSteps = resourceSteps;
    }

    private static boolean containsResource(Protos.TaskInfo taskInfo, Protos.Resource resource) {
        return ResourceUtils.getAllResources(taskInfo).stream()
                .anyMatch(taskInfoResource -> resourcesMatch(taskInfoResource, resource));
    }

    private static boolean resourcesMatch(Protos.Resource taskInfoResource, Protos.Resource resource) {
        return ResourceUtils.getResourceId(resource)
                .equals(ResourceUtils.getResourceId(taskInfoResource));
    }

    @Override
    public void record(OfferRecommendation offerRecommendation) throws Exception {
        if (!(offerRecommendation instanceof UninstallRecommendation)) {
            return;
        }

        // each offerRec ought to be tied to a resource with an ID
        UninstallRecommendation uninstallRecommendation = (UninstallRecommendation) offerRecommendation;
        Protos.Resource resource = uninstallRecommendation.getResource();
        logger.info("Marking resource as uninstalled: {}", TextFormat.shortDebugString(resource));

        // Find the tasks referencing the resource in this OfferRecommendation
        List<Protos.TaskInfo> tasksToUpdate = stateStore.fetchTasks().stream()
                .filter(taskSpec -> containsResource(taskSpec, resource))
                .collect(Collectors.toList());
        logger.info("Resource {} found in {} task(s).", resource.getName(), tasksToUpdate.size());
        tasksToUpdate.forEach(taskInfo ->
                logger.info("Resource {} found in task: {}",
                        resource.getName(),
                        TextFormat.shortDebugString(taskInfo)));
        if (!tasksToUpdate.isEmpty()) {
            stateStore.storeTasks(updateResources(resource, tasksToUpdate));

            // broadcast uninstallRecommendation to each UninstallStep in resource phase
            List<OfferRecommendation> uninstallRecommendations = Collections.singletonList(uninstallRecommendation);
            resourceSteps.forEach(step -> step.updateOfferStatus(uninstallRecommendations));
        }
    }

    private Collection<Protos.TaskInfo> updateResources(
            Protos.Resource resource, List<Protos.TaskInfo> tasksToUpdate) {
        // create new copies of taskinfos with updated resources
        Optional<String> initialResourceId = ResourceUtils.getResourceId(resource);
        List<Protos.TaskInfo> updatedTaskInfos = new ArrayList<>();
        if (!initialResourceId.isPresent()) {
            return updatedTaskInfos;
        }
        for (Protos.TaskInfo taskInfoToUpdate : tasksToUpdate) {
            Collection<Protos.Resource> updatedResources =
                    updatedResources(initialResourceId.get(), taskInfoToUpdate.getResourcesList());
            Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder(taskInfoToUpdate)
                    .clearResources()
                    .addAllResources(updatedResources)
                    .build();
            updatedTaskInfos.add(taskInfo);
        }
        return updatedTaskInfos;
    }

    private Collection<Protos.Resource> updatedResources(
            String initialResourceId, List<Protos.Resource> resourcesList) {
        // find the matching resource in each task and update its resource_id
        String uninstalledResourceId = TOMBSTONE_MARKER + initialResourceId;
        List<Protos.Resource> updatedResources = new ArrayList<>();
        for (Protos.Resource resource : resourcesList) {
            Optional<String> thisResourceId = ResourceUtils.getResourceId(resource);
            if (thisResourceId.isPresent() && initialResourceId.equals(thisResourceId.get())) {
                updatedResources.add(ResourceBuilder.fromExistingResource(resource)
                        .setResourceId(Optional.of(uninstalledResourceId))
                        .build());
            } else {
                updatedResources.add(resource);
            }
        }
        return updatedResources;
    }

}
