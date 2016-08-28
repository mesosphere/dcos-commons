package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.protobuf.ValueUtils;
import org.apache.mesos.specification.ResourceSpecification;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by gabriel on 8/27/16.
 */
public class DefaultBlockFactory implements BlockFactory {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StateStore stateStore;

    public DefaultBlockFactory(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public Block getBlock(TaskSpecification taskSpecification) throws InvalidRequirementException {
        Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskSpecification.getName());
        if (!taskInfoOptional.isPresent()) {
            return new DefaultBlock(
                    taskInfoOptional.get().getName(),
                    getNewOfferRequirement(taskSpecification),
                    Status.PENDING);
        } else {
            TaskSpecification oldTaskSpecification = taskInfoToTaskSpec(taskInfoOptional.get());
            Status status = getStatus(oldTaskSpecification, taskSpecification);
            if (status.equals(Status.COMPLETE)) {
                return new DefaultBlock(taskSpecification.getName());
            } else {
                return new DefaultBlock(
                        taskSpecification.getName(),
                        getExistingOfferRequirement(taskInfoOptional.get(), taskSpecification),
                        status);
            }
        }
    }

    private Status getStatus(TaskSpecification oldTaskSpecification, TaskSpecification newTaskSpecification) {
        if (oldTaskSpecification.equals(newTaskSpecification)) {
            Protos.TaskState taskState = stateStore.fetchStatus(newTaskSpecification.getName()).getState();
            switch (taskState) {
                case TASK_STAGING:
                case TASK_STARTING:
                    return Status.IN_PROGRESS;
                default:
                    return Status.COMPLETE;
            }
        } else {
            return Status.PENDING;
        }
    }

    private OfferRequirement getNewOfferRequirement(TaskSpecification taskSpecification) throws InvalidRequirementException {
        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder()
                .setName(taskSpecification.getName())
                .setTaskId(TaskUtils.emptyTaskId())
                .setSlaveId(TaskUtils.emptyAgentId())
                .addAllResources(getNewResources(taskSpecification))
                .build();

        return new OfferRequirement(Arrays.asList(taskInfo));
    }

    private Map<String, Protos.Resource> getResourceMap(Collection<Protos.Resource> resources) {
        Map<String, Protos.Resource> resourceMap = new HashMap<>();
        for (Protos.Resource resource : resources) {
            resourceMap.put(resource.getName(), resource);
        }

        return resourceMap;
    }


    private OfferRequirement getExistingOfferRequirement(
            Protos.TaskInfo oldTask,
            TaskSpecification newTaskSpecification) throws InvalidRequirementException {

        Map<String, Protos.Resource> oldResourceMap = getResourceMap(oldTask.getResourcesList());
        List<Protos.Resource> updatedResources = new ArrayList<>();

        for (ResourceSpecification resourceSpecification : newTaskSpecification.getResources()) {
            Protos.Resource oldResource = oldResourceMap.get(resourceSpecification.getName());
            if (oldResource != null) {
                updatedResources.add(updateResource(oldResource, resourceSpecification));
            } else {
                    updatedResources.add(
                            ResourceUtils.getDesiredResource(resourceSpecification));
            }
        }

        Protos.TaskInfo.Builder taskBuilder = Protos.TaskInfo.newBuilder(oldTask)
                .clearResources()
                .addAllResources(updatedResources)
                .clearTaskId()
                .clearSlaveId();

        return new OfferRequirement(Arrays.asList(taskBuilder.build()));
    }

    private Protos.Resource updateResource(Protos.Resource resource, ResourceSpecification resourceSpecification) {
        Protos.Resource.Builder builder = Protos.Resource.newBuilder(resource);
        switch (resource.getType()) {
            case SCALAR:
                return builder.setScalar(resourceSpecification.getValue().getScalar()).build();
            case RANGES:
                return builder.setRanges(resourceSpecification.getValue().getRanges()).build();
            case SET:
                return builder.setSet(resourceSpecification.getValue().getSet()).build();
            default:
                logger.error("Encountered unexpected Value type: " + resource.getType());
                return resource;
        }
    }

    private TaskSpecification taskInfoToTaskSpec(Protos.TaskInfo taskInfo) {
        return new TaskSpecification() {
            @Override
            public String getName() {
                return taskInfo.getName();
            }

            @Override
            public Protos.CommandInfo getCommand() {
                return taskInfo.getCommand();
            }

            @Override
            public Collection<ResourceSpecification> getResources() {
                Collection<ResourceSpecification> resourceSpecifications = new ArrayList<>();
                for (Protos.Resource resource : taskInfo.getResourcesList()) {
                    resourceSpecifications.add(new ResourceSpecification() {
                        @Override
                        public Protos.Value getValue() {
                            return ValueUtils.getValue(resource);
                        }

                        @Override
                        public String getName() {
                            return resource.getName();
                        }

                        @Override
                        public String getRole() {
                            return resource.getRole();
                        }

                        @Override
                        public String getPrincipal() {
                            return resource.getReservation().getPrincipal();
                        }
                    });
                }
                return null;
            }
        };
    }

    private Iterable<? extends Protos.Resource> getNewResources(TaskSpecification taskSpecification) {
        Collection<Protos.Resource> resources = new ArrayList<>();

        for (ResourceSpecification resourceSpecification : taskSpecification.getResources()) {
            resources.add(ResourceUtils.getDesiredResource(resourceSpecification));
        }

        return resources;
    }
}
