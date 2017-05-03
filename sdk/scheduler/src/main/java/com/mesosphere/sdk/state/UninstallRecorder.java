package com.mesosphere.sdk.state;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.OperationRecorder;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.UninstallRecommendation;
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
    public static final String TOMBSTONE_MARKER = "uninstalled_";
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

    private Collection<Protos.TaskInfo> updateResources(Protos.Resource resource,
                                                        List<Protos.TaskInfo> tasksToUpdate) {
        // create new copies of taskinfos with updated resources
        String initialResourceId = ResourceUtils.getResourceId(resource);
        List<Protos.TaskInfo> updatedTaskInfos = new ArrayList<>();
        for (Protos.TaskInfo taskInfoToUpdate : tasksToUpdate) {
            Collection<Protos.Resource> updatedResources = updatedResources(initialResourceId,
                    taskInfoToUpdate.getResourcesList());
            Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder(taskInfoToUpdate).clearResources()
                    .addAllResources(updatedResources).build();
            updatedTaskInfos.add(taskInfo);
        }
        return updatedTaskInfos;
    }

    private Collection<Protos.Resource> updatedResources(String initialResourceId,
                                                         List<Protos.Resource> resourcesList) {
        // find the matching resource in each task and update its resource_id
        String uninstalledResourceId = TOMBSTONE_MARKER + initialResourceId;
        List<Protos.Resource> updatedResources = new ArrayList<>();
        for (Protos.Resource resource : resourcesList) {
            if (initialResourceId.equals(ResourceUtils.getResourceId(resource))) {
                updatedResources.add(ResourceUtils.setResourceId(resource, uninstalledResourceId));
            } else {
                updatedResources.add(resource);
            }
        }
        return updatedResources;
    }

    private static boolean containsResource(Protos.TaskInfo taskInfo, Protos.Resource resource) {
        return taskInfo.getResourcesList().stream()
                .anyMatch(taskInfoResource -> resourcesMatch(taskInfoResource, resource));
    }

    private static boolean resourcesMatch(Protos.Resource taskInfoResource, Protos.Resource resource) {
        return ResourceUtils.getResourceId(resource).equals(ResourceUtils.getResourceId(taskInfoResource));
    }

}
