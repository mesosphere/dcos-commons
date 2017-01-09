package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This rule ensures that the given Offer is colocated with (or never colocated with) the specified
 * task 'type', whose retrieval from the TaskInfo is defined by the developer's implementation of a
 * {@link TaskTypeConverter}.
 *
 * For example, this can be used to colocate 'data' nodes with 'index' nodes, or to ensure that the
 * two are never colocated.
 */
public class TaskTypeRule implements PlacementRule {

    /**
     * Returns a {@link PlacementRule} which enforces avoidance of tasks which have the provided
     * type. For example, this could be used to ensure that 'data' nodes are never colocated with
     * 'index' nodes, or that 'data' nodes are never colocated with other 'data' nodes
     * (self-avoidance). Note that this rule is unidirectional; mutual avoidance requires
     * creating separate colocate rules for each direction.
     *
     * Note that if the avoided task does not already exist in the cluster, this will just pick a
     * random node, as there will be nothing to avoid.
     */
    public static PlacementRule avoid(String typeToAvoid, TaskTypeConverter typeConverter) {
        return new TaskTypeRule(typeToAvoid, typeConverter, BehaviorType.AVOID);
    }

    /**
     * Calls {@link #avoid(String, TaskTypeConverter) with a {@link TaskTypeLabelConverter}.
     */
    public static PlacementRule avoid(String typeToAvoid) {
        return avoid(typeToAvoid, null);
    }

    /**
     * Returns a {@link PlacementRule} which enforces colocation with tasks which have the provided
     * type. For example, this could be used to ensure that 'data' nodes are always colocated with
     * 'index' nodes. Note that this rule is unidirectional; mutual colocation requires
     * creating separate colocate rules for each direction.
     *
     * Note that if the colocated task does not already exist in the cluster, this will just pick a
     * random node. This behavior is to support defining mutual colocation: A colocates with B, and
     * B colocates with A. In this case one of the two directions won't see anything to colocate
     * with.
     */
    public static PlacementRule colocateWith(String typeToColocateWith, TaskTypeConverter typeConverter) {
        return new TaskTypeRule(typeToColocateWith, typeConverter, BehaviorType.COLOCATE);
    }

    /**
     * Calls {@link #colocateWith(String, TaskTypeConverter) with a {@link TaskTypeLabelConverter}.
     */
    public static PlacementRule colocateWith(String typeToColocateWith) {
        return colocateWith(typeToColocateWith, null);
    }

    /**
     * The behavior to be used.
     */
    private enum BehaviorType {
        COLOCATE,
        AVOID
    }

    private final String typeToFind;
    private final TaskTypeConverter typeConverter;
    private final BehaviorType behaviorType;

    @JsonCreator
    private TaskTypeRule(
            @JsonProperty("type") String typeToFind,
            @JsonProperty("converter") TaskTypeConverter typeConverter,
            @JsonProperty("behavior") BehaviorType behaviorType) {
        this.typeToFind = typeToFind;
        if (typeConverter == null) { // null when unspecified in serialized data
            typeConverter = new TaskTypeLabelConverter();
        }
        this.typeConverter = typeConverter;
        this.behaviorType = behaviorType;
    }

    @Override
    public EvaluationOutcome filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        List<TaskInfo> matchingTasks = new ArrayList<>();
        for (TaskInfo task : tasks) {
            if (typeToFind.equals(typeConverter.getTaskType(task))) {
                matchingTasks.add(task);
            }
        }
        // Create a rule which will handle most of the validation. Logic is deferred to avoid
        // double-counting a task against a prior version of itself.
        switch (behaviorType) {
        case AVOID:
            if (matchingTasks.isEmpty()) {
                // nothing to avoid, but this is expected when avoiding nodes of the same type
                // (self-avoidance), or when the developer has configured bidirectional rules
                // (A avoids B + B avoids A)
                return EvaluationOutcome.pass(
                        this,
                        "No tasks of avoided type '%s' are currently running.",
                        typeToFind);
            } else {
                return filterAvoid(offer, offerRequirement, matchingTasks);
            }
        case COLOCATE:
            if (matchingTasks.isEmpty()) {
                // nothing to colocate with! fall back to allowing any location.
                // this is expected when the developer has configured bidirectional rules
                // (A colocates with B + B colocates with A)
                return EvaluationOutcome.pass(
                        this,
                        "No tasks of colocated type '%s' are currently running.",
                        typeToFind);
            } else {
                return filterColocate(offer, offerRequirement, matchingTasks);
            }
        default:
            throw new IllegalStateException("Unsupported behavior type: " + behaviorType);
        }
    }

    /**
     * Implementation of task type avoidance. Considers the presence of tasks in the cluster to
     * determine whether the provided task can be launched against a given offer. This rule requires
     * that the offer be located on an agent which doesn't currently have an instance of the
     * specified task type.
     */
    private EvaluationOutcome filterAvoid(
            Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasksToAvoid) {
        for (TaskInfo taskToAvoid : tasksToAvoid) {
            if (PlacementUtils.areEquivalent(taskToAvoid, offerRequirement)) {
                // This is stale data for the same task that we're currently evaluating for
                // placement. Don't worry about avoiding it. This occurs when we're redeploying
                // a given task with a new configuration (old data not deleted yet).
                continue;
            }
            if (taskToAvoid.getSlaveId().equals(offer.getSlaveId())) {
                // The offer is for an agent which has a task to be avoided. Denied!
                return EvaluationOutcome.fail(this,
                        "Found a task matching avoided type '%s' on this agent.", typeToFind);
            }
        }
        // The offer doesn't match any tasks to avoid. Approved!
        return EvaluationOutcome.pass(this, "No tasks of avoided type '%s' found on this agent.", typeToFind);
    }

    /**
     * Implementation of task type colocation. Considers the presence of tasks in the cluster to
     * determine whether the provided task can be launched against a given offer. This rule requires
     * that the offer be located on an agent which currently has an instance of the specified task
     * type.
     */
    private EvaluationOutcome filterColocate(
            Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasksToColocate) {
        for (TaskInfo taskToColocate : tasksToColocate) {
            if (PlacementUtils.areEquivalent(taskToColocate, offerRequirement)) {
                // This is stale data for the same task that we're currently evaluating for
                // placement. Don't worry about colocating with it. This occurs when we're
                // redeploying a given task with a new configuration (old data not deleted yet).
                continue;
            }
            if (taskToColocate.getSlaveId().equals(offer.getSlaveId())) {
                // The offer is for an agent which has a task to colocate with. Approved!
                return EvaluationOutcome.pass(
                        this,
                        "Found a task matching colocated type '%s' on this agent.",
                        typeToFind);
            }
        }
        // The offer doesn't match any tasks to colocate with. Denied!
        return EvaluationOutcome.fail(this,
                "Didn't find a task matching colocated type '%s' on this agent.", typeToFind);
    }

    @JsonProperty("type")
    private String getType() {
        return typeToFind;
    }

    @JsonProperty("converter")
    private TaskTypeConverter getTypeConverter() {
        return typeConverter;
    }

    @JsonProperty("behavior")
    private BehaviorType getBehavior() {
        return behaviorType;
    }

    @Override
    public String toString() {
        return String.format("TaskTypeRule{type=%s, converter=%s, behavior=%s}",
            typeToFind, typeConverter, behaviorType);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
