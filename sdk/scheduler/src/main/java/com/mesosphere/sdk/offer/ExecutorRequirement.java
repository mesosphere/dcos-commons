package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.ExecutorEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.state.StateStore;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.ExecutorInfo;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An ExecutorRequirement encapsulates the needed resources an Executor must
 * have.
 */
public class ExecutorRequirement {
    private final String name;
    private final Collection<ResourceRequirement> resourceRequirements;
    private final Protos.ExecutorID executorId;
    private final boolean existingExecutor;

    public static ExecutorRequirement createNewExecutorRequirement(
            String name,
            Collection<ResourceRequirement> resourceRequirements) {
        Protos.ExecutorID executorId = Protos.ExecutorID.newBuilder()
                .setValue(name + "__" + UUID.randomUUID().toString())
                .build();
        return new ExecutorRequirement(name, executorId, false, resourceRequirements);
    }

    public static ExecutorRequirement createExistingExecutorRequirement(
            String name,
            Protos.ExecutorID executorId,
            Collection<ResourceRequirement> resourceRequirements) {
        return new ExecutorRequirement(name, executorId, true, resourceRequirements);
    }

    private ExecutorRequirement(
            String name,
            Protos.ExecutorID executorId,
            boolean existingExecutor,
            Collection<ResourceRequirement> resourceRequirements) {
        this.name = name;
        this.executorId = executorId;
        this.existingExecutor = existingExecutor;
        this.resourceRequirements = resourceRequirements;
    }

    public Collection<ResourceRequirement> getResourceRequirements() {
        return resourceRequirements;
    }

    public Collection<String> getResourceIds() {
        return RequirementUtils.getResourceIds(getResourceRequirements());
    }

    public Collection<String> getPersistenceIds() {
        return RequirementUtils.getPersistenceIds(getResourceRequirements());
    }

    public boolean isRunningExecutor() {
        return existingExecutor;
    }

    public OfferEvaluationStage getEvaluationStage() {
        return new ExecutorEvaluationStage(executorId);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
