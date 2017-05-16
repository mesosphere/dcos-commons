package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.LaunchEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelReader;

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
    private final String name;
    private Collection<ResourceRequirement> resourceRequirements;

    public TaskRequirement(String name, Collection<ResourceRequirement> resourceRequirements) {
        this.name = name;
        this.resourceRequirements = resourceRequirements;
    }

    public String getName() {
        return name;
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
