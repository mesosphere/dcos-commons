package com.mesosphere.sdk.scheduler.uninstall;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.Constants.TOMBSTONE_MARKER;

/**
 * Records to persistent storage the result of uninstalling/destroying resources in the process of installing the
 * service by marking them with a tombstone id, then notifying the uninstall plan of the changes.
 */
public class UninstallRecorder implements OperationRecorder {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StateStore stateStore;
    private final Collection<ResourceCleanupStep> resourceSteps;

    UninstallRecorder(StateStore stateStore, Collection<ResourceCleanupStep> resourceSteps) {
        this.stateStore = stateStore;
        this.resourceSteps = resourceSteps;
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
        if (tasksToUpdate.isEmpty()) {
            return;
        }

        logger.info("Resource {}/{} found in {} task{}: {}",
                resource.getName(),
                ResourceUtils.getResourceId(resource),
                tasksToUpdate.size(),
                tasksToUpdate.size() == 1 ? "" : "s",
                tasksToUpdate.stream().map(Protos.TaskInfo::getName).collect(Collectors.toList()));

        stateStore.storeTasks(updateResources(resource, tasksToUpdate));

        // Broadcast the resulting uninstallRecommendation to each resource step in the uninstall plan.
        // We need to manually pass the uninstall recommendation to the resource cleanup steps. They do not get this
        // information via DefaultPlanScheduler because that only handles deployment (and therefore is not used by
        // UninstallScheduler), whereas these are handled via the ResourceCleanerScheduler.
        List<OfferRecommendation> uninstallRecommendations = Collections.singletonList(uninstallRecommendation);
        resourceSteps.forEach(step -> step.updateOfferStatus(uninstallRecommendations));
    }

    private static boolean containsResource(Protos.TaskInfo taskInfo, Protos.Resource resource) {
        return ResourceUtils.getAllResources(taskInfo).stream()
                .anyMatch(taskInfoResource -> resourcesMatch(taskInfoResource, resource));
    }

    private static boolean resourcesMatch(Protos.Resource resource1, Protos.Resource resource2) {
        return ResourceUtils.getResourceId(resource1).equals(ResourceUtils.getResourceId(resource2));
    }

    private static Collection<Protos.TaskInfo> updateResources(
            Protos.Resource resource, Collection<Protos.TaskInfo> tasksToUpdate) {
        // create new copies of taskinfos with updated resources
        final Optional<String> initialResourceId = ResourceUtils.getResourceId(resource);
        Collection<Protos.TaskInfo> updatedTaskInfos = new ArrayList<>();
        if (!initialResourceId.isPresent()) {
            return updatedTaskInfos;
        }
        for (Protos.TaskInfo taskInfoToUpdate : tasksToUpdate) {
            updatedTaskInfos.add(Protos.TaskInfo.newBuilder(taskInfoToUpdate)
                    .clearResources()
                    .addAllResources(updatedResources(initialResourceId.get(), taskInfoToUpdate.getResourcesList()))
                    .build());
        }
        return updatedTaskInfos;
    }

    private static Collection<Protos.Resource> updatedResources(
            String initialResourceId, Collection<Protos.Resource> resources) {
        // find the matching resource in each task and update its resource_id
        final String uninstalledResourceId = TOMBSTONE_MARKER + initialResourceId;
        Collection<Protos.Resource> updatedResources = new ArrayList<>();
        for (Protos.Resource resource : resources) {
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
