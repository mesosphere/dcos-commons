package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.ExecutorRequirement;
import com.mesosphere.sdk.offer.OfferRequirement;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
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

    public PodInfoBuilder(OfferRequirement offerRequirement) {
        this.offerRequirement = offerRequirement;
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

    private static Protos.TaskInfo.Builder clearResources(Protos.TaskInfo.Builder builder) {
        builder.clearResources();

        return builder;
    }

    private static Protos.ExecutorInfo.Builder clearResources(Protos.ExecutorInfo.Builder builder) {
        builder.clearResources();

        return builder;
    }
}
