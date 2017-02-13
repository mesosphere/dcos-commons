package com.mesosphere.sdk.offer.evaluate;

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
        executorBuilder = offerRequirement.getExecutorRequirementOptional().isPresent() ?
                offerRequirement.getExecutorRequirementOptional().get().getExecutorInfo().toBuilder() :
                null;
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
}
