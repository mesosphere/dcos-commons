package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.OperationRecorder;
import com.mesosphere.sdk.offer.ResourceBuilder;
import com.mesosphere.sdk.offer.ResourceCollectUtils;
import com.mesosphere.sdk.offer.UninstallRecommendation;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.Constants.TOMBSTONE_MARKER;

/**
 * Records to persistent storage the result of uninstalling/destroying resources.
 */
public class UninstallRecorder implements OperationRecorder {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StateStore stateStore;
    private final Phase resourcePhase;

    UninstallRecorder(StateStore stateStore, Phase resourcePhase) {
        this.stateStore = stateStore;
        this.resourcePhase = resourcePhase;
    }

    private static boolean containsResource(Protos.TaskInfo taskInfo, Protos.Resource resource) {
        return taskInfo.getResourcesList().stream()
                .anyMatch(taskInfoResource -> resourcesMatch(taskInfoResource, resource));
    }

    private static boolean resourcesMatch(Protos.Resource taskInfoResource, Protos.Resource resource) {
        return ResourceCollectUtils.getResourceId(resource)
                .equals(ResourceCollectUtils.getResourceId(taskInfoResource));
    }

    @Override
    public void record(OfferRecommendation offerRecommendation) throws Exception {
        if (!(offerRecommendation instanceof UninstallRecommendation)) {
            return;
        }

        // each offerRec ought to be tied to a resource with an ID
        UninstallRecommendation uninstallRecommendation = (UninstallRecommendation) offerRecommendation;
        Protos.Resource resource = uninstallRecommendation.getResource();
        logger.info("Marking resource as uninstalled: {}", resource);

        // Find the tasks referencing the resource in this OfferRecommendation
        List<Protos.TaskInfo> tasksToUpdate = stateStore.fetchTasks().stream()
                .filter(taskSpec -> containsResource(taskSpec, resource))
                .collect(Collectors.toList());
        logger.info("Resource found in tasks: {}", tasksToUpdate);
        if (!tasksToUpdate.isEmpty()) {
            stateStore.storeTasks(updateResources(resource, tasksToUpdate));

            // broadcast uninstallRecommendation to each UninstallStep in resource phase
            List<OfferRecommendation> uninstallRecommendations = Collections.singletonList(uninstallRecommendation);
            resourcePhase.getChildren().forEach(step -> step.updateOfferStatus(uninstallRecommendations));
        }
    }

    private Collection<Protos.TaskInfo> updateResources(
            Protos.Resource resource, List<Protos.TaskInfo> tasksToUpdate) {
        // create new copies of taskinfos with updated resources
        Optional<String> initialResourceId = ResourceCollectUtils.getResourceId(resource);
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
            Optional<String> thisResourceId = ResourceCollectUtils.getResourceId(resource);
            if (thisResourceId.isPresent() && initialResourceId.equals(thisResourceId.get())) {
                updatedResources.add(ResourceBuilder.fromExistingResource(resource)
                        .setResourceId(uninstalledResourceId)
                        .build());
            } else {
                updatedResources.add(resource);
            }
        }
        return updatedResources;
    }

}
