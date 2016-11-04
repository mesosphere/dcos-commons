package org.apache.mesos.offer;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.constrain.PlacementRule;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * An OfferRequirement encapsulates the needed resources that an {@link Offer} must have in order to
 * launch a task against that {@link Offer}.
 *
 * In general these are Resource requirements, such as requiring a certain amount of cpu, memory,
 * and disk, as encapsulated by {@link TaskRequirement} and {@link ExecutorRequirement}.
 * More dynamic requirements may also be defined on the placement of the new task, as evaluated
 * by the provided {@link PlacementRule}s.
 */
public class OfferRequirement {
    private final String taskType;
    private final Collection<TaskRequirement> taskRequirements;
    private final Optional<ExecutorRequirement> executorRequirementOptional;
    private final Collection<PlacementRule> placementRules;

    /**
     * Creates a new {@link OfferRequirement}.
     *
     * @param taskType the task type name from the TaskSet, used for placement filtering
     * @param taskInfos the 'draft' {@link TaskInfo}s from which task requirements should be generated
     * @param executorInfoOptional the executor from which an executor requirement should be
     *     generated, if any
     * @param placementRuleGeneratorOptional the placement constraints which should be applied to the
     *     tasks, if any
     * @throws InvalidRequirementException if task or executor requirements could not be generated
     *     from the provided information
     */
    public OfferRequirement(
            String taskType,
            Collection<TaskInfo> taskInfos,
            Optional<ExecutorInfo> executorInfoOptional,
            Collection<PlacementRule> placementRules)
                    throws InvalidRequirementException {
        /*
        this(
                taskType,
                getTaskRequirementsInternal(taskInfos),
                executorInfoOptional.isPresent() ?
                        Optional.of(ExecutorRequirement.create(executorInfoOptional.get())) :
                        Optional.empty(),
                placementRules);
         */
        this.taskType = taskType;
        this.taskRequirements = getTaskRequirementsInternal(taskInfos);
        this.executorRequirementOptional = executorInfoOptional.isPresent() ?
                Optional.of(ExecutorRequirement.create(executorInfoOptional.get())) :
                Optional.empty();
        this.placementRules = placementRules;
    }

    /**
     * Creates a new {@link OfferRequirement} with provided executor requirement and empty placement
     * constraints.
     *
     * @see #OfferRequirement(String, Collection, Optional, Optional)
     */
    public OfferRequirement(
            String taskType,
            Collection<TaskInfo> taskInfos,
            Optional<ExecutorInfo> executorInfoOptional)
                    throws InvalidRequirementException {
        this(taskType, taskInfos, executorInfoOptional, Optional.empty());
    }

    /**
     * Creates a new {@link OfferRequirement} with empty executor requirement and empty placement
     * constraints.
     *
     * @see #OfferRequirement(String, Collection, Optional, Optional)
     */
    public OfferRequirement(String taskType, Collection<TaskInfo> taskInfos)
            throws InvalidRequirementException {
        this(taskType, taskInfos, Optional.empty());
    }

    /**
     * Creates and returns a new {@link OfferRequirement} with any placement rules removed.
     */
    public OfferRequirement withoutPlacementRules() {
        return new OfferRequirement(taskType, taskRequirements, executorRequirementOptional, Collections.emptyList());
    }

    /*
    private OfferRequirement(
            String taskType,
            Collection<TaskRequirement> taskRequirements,
            Optional<ExecutorRequirement> executorRequirementOptional,
            Collection<PlacementRule> placementRules) {
        this.taskType = taskType;
        this.taskRequirements = taskRequirements;
        this.executorRequirementOptional = executorRequirementOptional;
        this.placementRules = placementRules;
    }
    */

    public String getTaskType() {
        return taskType;
    }

    public Collection<TaskRequirement> getTaskRequirements() {
        return taskRequirements;
    }

    public Optional<ExecutorRequirement> getExecutorRequirementOptional() {
        return executorRequirementOptional;
    }

    public Optional<PlacementRuleGenerator> getPlacementRuleGeneratorOptional() {
        return placementRuleGeneratorOptional;
    }

    public Collection<String> getResourceIds() {
        Collection<String> resourceIds = new ArrayList<String>();

        for (TaskRequirement taskReq : taskRequirements) {
            resourceIds.addAll(taskReq.getResourceIds());
        }

        if (executorRequirementOptional.isPresent()) {
            resourceIds.addAll(executorRequirementOptional.get().getResourceIds());
        }

        return resourceIds;
    }

    public Collection<String> getPersistenceIds() {
        Collection<String> persistenceIds = new ArrayList<String>();

        for (TaskRequirement taskReq : taskRequirements) {
            persistenceIds.addAll(taskReq.getPersistenceIds());
        }

        if (executorRequirementOptional.isPresent()) {
            persistenceIds.addAll(executorRequirementOptional.get().getPersistenceIds());
        }

        return persistenceIds;
    }

    private static Collection<TaskRequirement> getTaskRequirementsInternal(
            Collection<TaskInfo> taskInfos) throws InvalidRequirementException {
        Collection<TaskRequirement> taskRequirements = new ArrayList<TaskRequirement>();
        for (TaskInfo taskInfo : taskInfos) {
            taskRequirements.add(new TaskRequirement(taskInfo));
        }
        return taskRequirements;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
