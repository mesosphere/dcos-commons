package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.LaunchEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelReader;

import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.TaskSpec;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.state.StateStore;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * A TaskRequirement encapsulates the needed resources a Task must have.
 */
public class TaskRequirement {
    private final TaskSpec taskSpec;
    private final PodInstance podInstance;
    private Collection<ResourceRequirement> resourceRequirements;

    public TaskRequirement(
            TaskSpec taskSpec,
            PodInstance podInstance,
            Collection<ResourceRequirement> resourceRequirements) {
        this.taskSpec = taskSpec;
        this.podInstance = podInstance;
        this.resourceRequirements = resourceRequirements;
    }

    public String getName() {
        return taskSpec.getName();
    }

    public String getType() {
        return podInstance.getPod().getType();
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

    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new LaunchEvaluationStage(taskName);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
