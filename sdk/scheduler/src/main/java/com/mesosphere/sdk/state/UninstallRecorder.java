package com.mesosphere.sdk.state;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.UninstallScheduler;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Records to persistent storage the result of uninstalling/destroying resources.
 */
public class UninstallRecorder implements OperationRecorder {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StateStore stateStore;
    private final ServiceSpec serviceSpec;
    private final Scheduler uninstallScheduler;

    public UninstallRecorder(StateStore stateStore, ServiceSpec serviceSpec, UninstallScheduler uninstallScheduler) {
        this.stateStore = stateStore;
        this.serviceSpec = serviceSpec;
        this.uninstallScheduler = uninstallScheduler;
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

        stateStore.storeTasks(updateResources(resource, tasksToUpdate));
    }

    private Collection<Protos.TaskInfo> updateResources(Protos.Resource resource, List<Protos.TaskInfo> tasksToUpdate) {
        // create new copies of taskinfo's with updated resources
        String initialResourceId = ResourceUtils.getResourceId(resource);
        List<Protos.TaskInfo> updatedTaskInfos = new ArrayList<>();
        for (Protos.TaskInfo taskInfoToUpdate : tasksToUpdate) {
            Collection<Protos.Resource> updatedResources = markResources(initialResourceId,
                    taskInfoToUpdate.getResourcesList());
            Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder(taskInfoToUpdate).clearResources()
                    .addAllResources(updatedResources).build();
            updatedTaskInfos.add(taskInfo);
        }
        return updatedTaskInfos;
    }

    private Collection<Protos.Resource> markResources(String initialResourceId, List<Protos.Resource> resourcesList) {
        // find the matching resource in each task and update its resource_id
        String uninstalledResourceId = "uninstalled_" + initialResourceId;
        List<Protos.Resource> markedResources = new ArrayList<>();
        for (Protos.Resource resource : resourcesList) {
            Protos.Resource.Builder resourceBuilder = resource.toBuilder();
            if (initialResourceId.equals(ResourceUtils.getResourceId(resource))) {
                Protos.Labels.Builder labelsBuilder = resourceBuilder.getReservationBuilder().getLabelsBuilder();
                for (Protos.Label.Builder label : labelsBuilder.getLabelsBuilderList()) {
                    if (label.getKey().equals(MesosResource.RESOURCE_ID_KEY)) {
                        label.setValue(uninstalledResourceId);
                    }
                }

            }
            markedResources.add(resourceBuilder.build());
        }
        return markedResources;
    }

    private boolean containsResource(Protos.TaskInfo taskInfo, Protos.Resource resource) {
        return taskInfo.getResourcesList().stream()
                .anyMatch(taskInfoResource -> resourcesMatch(taskInfoResource, resource));
    }

    private boolean resourcesMatch(Protos.Resource taskInfoResource, Protos.Resource resource) {
        return ResourceUtils.getResourceId(resource).equals(ResourceUtils.getResourceId(taskInfoResource));
    }

}
