package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.ExecutorEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.Optional;

/**
 * An ExecutorRequirement encapsulates the needed resources an Executor must
 * have.
 */
public class ExecutorRequirement {
    private final String name;
    private final Protos.ExecutorID executorId;
    private final Collection<ResourceRequirement> resourceRequirements;

    public static ExecutorRequirement createNewExecutorRequirement(
            String name,
            Collection<ResourceRequirement> resourceRequirements) {
        return new ExecutorRequirement(name, null, resourceRequirements);
    }

    public static ExecutorRequirement createExistingExecutorRequirement(
            String name,
            Protos.ExecutorID executorId,
        Collection<ResourceRequirement> resourceRequirements) {
        return new ExecutorRequirement(name, executorId, resourceRequirements);
    }

    private ExecutorRequirement(
            String name,
            Protos.ExecutorID executorId,
            Collection<ResourceRequirement> resourceRequirements) {
        this.name = name;
        this.executorId = executorId;
        this.resourceRequirements = resourceRequirements;
    }

    public boolean isRunningExecutor() {
        return getExecutorId().isPresent();
    }

    public Optional<Protos.ExecutorID> getExecutorId() {
        return Optional.ofNullable(executorId);
    }

    public OfferEvaluationStage getEvaluationStage() {
        return new ExecutorEvaluationStage(executorId);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
