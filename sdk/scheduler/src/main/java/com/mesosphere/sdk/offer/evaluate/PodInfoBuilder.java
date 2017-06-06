package com.mesosphere.sdk.offer.evaluate;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.*;
import org.apache.mesos.Protos;

import java.util.*;
import java.util.stream.Collectors;


/**
 * A {@link PodInfoBuilder} encompasses a mutable group of {@link org.apache.mesos.Protos.TaskInfo.Builder}s and,
 * optionally, a {@link org.apache.mesos.Protos.ExecutorInfo.Builder}. This supports the modification of task infos
 * during the evaluation process, allowing e.g. dynamic ports to be represented as environment variables in the task
 * to which they are attached.
 */
public class PodInfoBuilder {
    private final OfferRequirement offerRequirement;
    private final Map<String, Protos.TaskInfo.Builder> taskBuilders;
    private final Protos.ExecutorInfo.Builder executorBuilder;

    private Set<Integer> assignedOverlayPorts = new HashSet<>();

    public PodInfoBuilder(OfferRequirement offerRequirement) {
        this.offerRequirement = offerRequirement;

        // add all of the port resource requirements to the assignedOverlayPorts to make sure that we don't dynamically
        // assign an overlay port that we're going to explicitly request later.
        offerRequirement.getTaskRequirements().forEach(taskRequirement ->
            taskRequirement.getResourceRequirements().stream()
                .filter(resourceRequirement -> resourceRequirement.getName().equals(Constants.PORTS_RESOURCE_TYPE))
                .collect(Collectors.toList())
                    .forEach(resourceRequirement ->
                        assignedOverlayPorts.add((int) resourceRequirement.getValue()
                            .getRanges().getRange(0).getBegin())));

        taskBuilders = offerRequirement.getTaskRequirements().stream()
                .map(r -> r.getTaskInfo())
                .collect(Collectors.toMap(t -> t.getName(), t -> clearResources(t.toBuilder())));

        Optional<ExecutorRequirement> executorRequirement = offerRequirement.getExecutorRequirementOptional();
        executorBuilder = executorRequirement.isPresent() ?
                executorRequirement.get().getExecutorInfo().toBuilder() :
                null;
        // If this executor is already running, we won't be claiming any new resources for it, and we want to make sure
        // to provide an identical ExecutorInfo to Mesos for any other tasks that are going to launch in this pod.
        // So don't clear the resources on the existing protobuf, we're just going to pass it as is.
        if (executorBuilder != null && !executorRequirement.get().isRunningExecutor()) {
            clearResources(executorBuilder);
        }
    }

    public OfferRequirement getOfferRequirement() {
        return offerRequirement;
    }

    public Protos.TaskInfo.Builder getTaskBuilder(String taskName) {
        return taskBuilders.get(taskName);
    }

    public Optional<Protos.ExecutorInfo.Builder> getExecutorBuilder() {
        return Optional.ofNullable(executorBuilder);
    }

    public Collection<Protos.Resource.Builder> getResourceBuilders() {
        return taskBuilders.values().stream()
                .map(t -> t.getResourcesBuilderList())
                .flatMap(xs -> xs.stream())
                .collect(Collectors.toList());
    }

    public boolean isAssignedOverlayPort(Integer candidatePort) {
        return assignedOverlayPorts.contains(candidatePort);
    }

    public void addAssignedOverlayPort(int port) {
        assignedOverlayPorts.add(port);
    }

    @VisibleForTesting
    public Set<Integer> getAssignedOverlayPorts() {
        return assignedOverlayPorts;
    }

    private static Protos.TaskInfo.Builder clearResources(Protos.TaskInfo.Builder builder) {
        builder.clearResources();

        return builder;
    }

    private static Protos.ExecutorInfo.Builder clearResources(Protos.ExecutorInfo.Builder builder) {
        builder.clearResources();

        return builder;
    }
}
