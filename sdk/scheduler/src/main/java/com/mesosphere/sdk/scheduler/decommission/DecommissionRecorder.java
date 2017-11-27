package com.mesosphere.sdk.scheduler.decommission;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Records to persistent storage the result of uninstalling/destroying resources in the process of decommissioning pods,
 * by removing the destroyed resources from the stored TaskInfos.
 */
public class DecommissionRecorder implements OperationRecorder {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StateStore stateStore;
    private final Collection<Step> resourceSteps;

    public DecommissionRecorder(StateStore stateStore, Collection<Step> resourceSteps) {
        this.stateStore = stateStore;
        this.resourceSteps = resourceSteps;
    }

    @Override
    public void record(OfferRecommendation offerRecommendation) throws Exception {
        if (!(offerRecommendation instanceof UninstallRecommendation)) {
            return;
        }
        Protos.Resource resource = ((UninstallRecommendation) offerRecommendation).getResource();

        // Find any decommissioning tasks which reference the resource:
        List<Protos.TaskInfo> tasksToUpdate = stateStore.fetchTasks().stream()
                .filter(task -> containsResource(task, resource)
                        && stateStore.fetchGoalOverrideStatus(task.getName())
                                .equals(DecommissionPlanFactory.DECOMMISSIONING_STATUS))
                .collect(Collectors.toList());
        if (tasksToUpdate.isEmpty()) {
            return;
        }

        logger.info("Resource {}/{} found in {} decommissioning task{}: {}",
                resource.getName(),
                ResourceUtils.getResourceId(resource),
                tasksToUpdate.size(),
                tasksToUpdate.size() == 1 ? "" : "s",
                tasksToUpdate.stream().map(Protos.TaskInfo::getName).collect(Collectors.toList()));

        stateStore.storeTasks(removeResource(tasksToUpdate, resource));

        // We need to manually pass the uninstall recommendation to the resource cleanup steps. They do not get this
        // information via DefaultPlanScheduler because that only handles deployment, whereas these are handled via
        // the ResourceCleanerScheduler.
        List<OfferRecommendation> offerRecommendations = Collections.singletonList(offerRecommendation);
        resourceSteps.forEach(step -> step.updateOfferStatus(offerRecommendations));
    }

    private static boolean containsResource(Protos.TaskInfo taskInfo, Protos.Resource resource) {
        return ResourceUtils.getAllResources(taskInfo).stream().anyMatch(taskResource ->
                ResourceUtils.getResourceId(taskResource).equals(ResourceUtils.getResourceId(resource)));
    }

    private static Collection<Protos.TaskInfo> removeResource(
            Collection<Protos.TaskInfo> tasksToUpdate, Protos.Resource resourceToRemove) {
        // create new copies of taskinfos with updated resources
        Optional<String> resourceId = ResourceUtils.getResourceId(resourceToRemove);
        List<Protos.TaskInfo> updatedTaskInfos = new ArrayList<>();
        if (!resourceId.isPresent()) {
            return updatedTaskInfos;
        }
        for (Protos.TaskInfo taskInfoToUpdate : tasksToUpdate) {
            updatedTaskInfos.add(Protos.TaskInfo.newBuilder(taskInfoToUpdate)
                    .clearResources()
                    .addAllResources(removeResource(taskInfoToUpdate.getResourcesList(), resourceId.get()))
                    .build());
        }
        return updatedTaskInfos;
    }

    private static Collection<Protos.Resource> removeResource(
            Collection<Protos.Resource> resourcesToFilter, String resourceIdToRemove) {
        Collection<Protos.Resource> filteredResources = new ArrayList<>();
        for (Protos.Resource resource : resourcesToFilter) {
            Optional<String> thisResourceId = ResourceUtils.getResourceId(resource);
            if (!thisResourceId.isPresent() || !thisResourceId.get().equals(resourceIdToRemove)) {
                filteredResources.add(resource);
            }
        }
        return filteredResources;
    }
}
