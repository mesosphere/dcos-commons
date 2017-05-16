package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.TaskInfo;

import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An OfferRequirement encapsulates the needed resources that an {@link org.apache.mesos.Protos.Offer} must have in
 * order to launch a task against that {@link org.apache.mesos.Protos.Offer}.
 *
 * In general these are Resource requirements, such as requiring a certain amount of cpu, memory,
 * and disk, as encapsulated by {@link TaskRequirement} and {@link ExecutorRequirement}.
 * More dynamic requirements may also be defined on the placement of the new task, as evaluated
 * by the provided {@link PlacementRule}s.
 */
public class OfferRequirement {
    private final String type;
    private final Map<String, TaskRequirement> taskRequirements;
    private final Optional<ExecutorRequirement> executorRequirementOptional;
    private final Optional<PlacementRule> placementRuleOptional;
    private final int index;


    public static OfferRequirement create(
            String taskType,
            int index,
            Collection<TaskRequirement> taskRequirements,
            ExecutorRequirement executorRequirement,
            Optional<PlacementRule> placementRuleOptional) {
        return new OfferRequirement(
                taskType, index, taskRequirements, Optional.ofNullable(executorRequirement), placementRuleOptional);
    }

    /**
     * Creates and returns a new {@link OfferRequirement} with any placement rules removed.
     */
    public OfferRequirement withoutPlacementRules() {
        return new OfferRequirement(
                type, index, taskRequirements.values(), executorRequirementOptional, Optional.empty());
    }

    public OfferRequirement(
            String type,
            int index,
            Collection<TaskRequirement> taskRequirements,
            Optional<ExecutorRequirement> executorRequirementOptional,
            Optional<PlacementRule> placementRuleOptional) {
        this.type = type;
        this.index = index;
        this.taskRequirements = taskRequirements.stream()
                .collect(Collectors.toMap(t -> t.getName(), Function.identity()));
        this.executorRequirementOptional = executorRequirementOptional;
        this.placementRuleOptional = placementRuleOptional;
    }

    public String getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }

    public Collection<TaskRequirement> getTaskRequirements() {
        return taskRequirements.values();
    }

    public Optional<ExecutorRequirement> getExecutorRequirementOptional() {
        return executorRequirementOptional;
    }

    public Optional<PlacementRule> getPlacementRuleOptional() {
        return placementRuleOptional;
    }

    public Collection<Protos.Resource> getResources() {
        throw new UnsupportedOperationException("THIS METHOD SHOULD NOT EXIST");
    }

    public Collection<String> getResourceIds() {
        Collection<String> resourceIds = new ArrayList<String>();

        for (TaskRequirement taskReq : taskRequirements.values()) {
            resourceIds.addAll(taskReq.getResourceIds());
        }

        return resourceIds;
    }

    public Collection<String> getPersistenceIds() {
        Collection<String> persistenceIds = new ArrayList<String>();

        for (TaskRequirement taskReq : taskRequirements.values()) {
            persistenceIds.addAll(taskReq.getPersistenceIds());
        }

        return persistenceIds;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
