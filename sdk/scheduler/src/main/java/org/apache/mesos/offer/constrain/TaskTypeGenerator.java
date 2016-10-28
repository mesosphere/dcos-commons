package org.apache.mesos.offer.constrain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


/**
 * This rule ensures that the given Offer is colocated with (or never colocated with) the specified
 * task 'type', whose retrieval from the TaskInfo is defined by the developer's implementation of a
 * {@link TaskTypeConverter}.
 *
 * For example, this can be used to colocate 'data' nodes with 'index' nodes, or to ensure that the
 * two are never colocated.
 */
public class TaskTypeGenerator implements PlacementRuleGenerator {

    /**
     * Given a {@link TaskInfo}, returns a type string for that task. This must be implemented by
     * the developer, or see {@link TaskIDDashConverter} for a sample implementation which expects
     * task ids of the form "tasktypehere-0__uuid".
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    public interface TaskTypeConverter {
        public String getTaskType(TaskInfo taskInfo);
    }

    /**
     * Implementation of {@link TaskTypeConverter} which expects a Label which provides the task type.
     *
     * @throws IllegalArgumentException if the provided task doesn't have a task type label
     */
    public static class TaskTypeLabelConverter implements TaskTypeConverter {
        @Override
        public String getTaskType(TaskInfo taskInfo) {
            try {
                return TaskUtils.getTaskType(taskInfo);
            } catch (TaskException e) {
                throw new IllegalArgumentException(String.format(
                        "Unable to extract task type label from provided TaskInfo: %s", taskInfo), e);
            }
        }

        @Override
        public String toString() {
            return String.format("TaskTypeLabelConverter{}");
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

    /**
     * Returns a PlacementRuleGenerator which enforces avoidance of tasks which have the provided
     * type. For example, this could be used to ensure that 'data' nodes are never colocated with
     * 'index' nodes, or that 'data' nodes are never colocated with other 'data' nodes
     * (self-avoidance). Note that this rule is unidirectional; mutual avoidance requires
     * creating separate colocate rules for each direction.
     *
     * Note that if the avoided task does not already exist in the cluster, this will just pick a
     * random node, as there will be nothing to avoid.
     */
    public static PlacementRuleGenerator createAvoid(
            String typeToAvoid, TaskTypeConverter typeConverter) {
        return new TaskTypeGenerator(typeToAvoid, typeConverter, BehaviorType.AVOID);
    }

    /**
     * Calls {@link #createAvoid(String, TaskTypeConverter) with a {@link TaskTypeLabelConverter}.
     */
    public static PlacementRuleGenerator createAvoid(String typeToAvoid) {
        return createAvoid(typeToAvoid, new TaskTypeLabelConverter());
    }

    /**
     * Returns a PlacementRuleGenerator which enforces colocation with tasks which have the provided
     * type. For example, this could be used to ensure that 'data' nodes are always colocated with
     * 'index' nodes. Note that this rule is unidirectional; mutual colocation requires
     * creating separate colocate rules for each direction.
     *
     * Note that if the colocated task does not already exist in the cluster, this will just pick a
     * random node. This behavior is to support defining mutual colocation: A colocates with B, and
     * B colocates with A. In this case one of the two directions won't see anything to colocate
     * with.
     */
    public static PlacementRuleGenerator createColocate(
            String typeToColocateWith, TaskTypeConverter typeConverter) {
        return new TaskTypeGenerator(typeToColocateWith, typeConverter, BehaviorType.COLOCATE);
    }

    /**
     * Calls {@link #createColocate(String, TaskTypeConverter) with a {@link TaskTypeLabelConverter}.
     */
    public static PlacementRuleGenerator createColocate(String typeToColocateWith) {
        return createColocate(typeToColocateWith, new TaskTypeLabelConverter());
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
    private TaskTypeGenerator(
            @JsonProperty("type") String typeToFind,
            @JsonProperty("converter") TaskTypeConverter typeConverter,
            @JsonProperty("behavior") BehaviorType behaviorType) {
        this.typeToFind = typeToFind;
        this.typeConverter = typeConverter;
        this.behaviorType = behaviorType;
    }

    @Override
    public PlacementRule generate(Collection<TaskInfo> tasks) {
        Set<String> agentsWithMatchingType = new HashSet<>();
        for (TaskInfo task : tasks) {
            if (typeToFind.equals(typeConverter.getTaskType(task))) {
                // Matching task type found. Target (or avoid) this agent.
                agentsWithMatchingType.add(task.getSlaveId().getValue());
            }
        }

        List<PlacementRule> agentRules = new ArrayList<>();
        for (String agent : agentsWithMatchingType) {
            agentRules.add(new AgentRule(agent));
        }
        switch (behaviorType) {
        case COLOCATE:
            if (agentRules.isEmpty()) {
                // nothing to colocate with! fall back to allowing any location.
                // this is expected when the developer has configured bidirectional rules
                // (A colocates with B + B colocates with A)
                return new PassthroughRule(
                        String.format("no tasks of type '%s' to colocate with", typeToFind));
            } else {
                return new OrRule(agentRules);
            }
        case AVOID:
            if (agentRules.isEmpty()) {
                // nothing to avoid, but this is expected when avoiding nodes of the same type
                // (self-avoidance), or when the developer has configured bidirectional rules
                // (A avoids B + B avoids A)
                return new PassthroughRule(
                        String.format("no tasks of type '%s' to avoid", typeToFind));
            } else {
                return new NotRule(new OrRule(agentRules));
            }
        default:
            throw new IllegalStateException("Unsupported behavior type: " + behaviorType);
        }
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
        return String.format("TaskTypeGenerator{type=%s, converter=%s, behavior=%s}",
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
