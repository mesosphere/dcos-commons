package org.apache.mesos.offer.constrain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.executor.DcosTaskConstants;
import org.apache.mesos.offer.TaskUtils;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * This rule ensures that the given Offer is colocated with the specified task 'type', whose
 * retrieval from the TaskInfo is defined by the developer's implementation of a
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
    public interface TaskTypeConverter {
        public String getTaskType(TaskInfo taskInfo);
    }

    /**
     * Implementation of {@link TaskTypeConverter} which expects TaskIDs of the form
     * "tasktypehere-0__uuid" => "tasktypehere".
     *
     * @see TaskUtils#toTaskType(org.apache.mesos.Protos.TaskID)
     */
    public static class TaskIDDashConverter implements TaskTypeConverter {
        @Override
        public String getTaskType(TaskInfo taskInfo) {
            return TaskUtils.toTaskType(taskInfo.getTaskId());
        }
    }

    /**
     * Implementation of {@link TaskTypeConverter} which expects an envvar providing the task type
     * named {@link DcosTaskConstants.TASK_TYPE}.
     *
     * @throws IllegalArgumentException if the provided task doesn't have TASK_TYPE in its env
     */
    public static class TaskEnvMapConverter implements TaskTypeConverter {
        @Override
        public String getTaskType(TaskInfo taskInfo) {
            Protos.CommandInfo commandInfo;
            try {
                commandInfo = Protos.CommandInfo.parseFrom(taskInfo.getData());
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException(String.format(
                        "Unable to extract CommandInfo from provided TaskInfo: %s", taskInfo), e);
            }
            for (Protos.Environment.Variable var : commandInfo.getEnvironment().getVariablesList()) {
                if (var.getName().equals(DcosTaskConstants.TASK_TYPE)) {
                    return var.getValue();
                }
            }
            throw new IllegalArgumentException(String.format(
                    "Unable to find environment variable named '%s' in commandInfo: %s",
                    DcosTaskConstants.TASK_TYPE, commandInfo));
        }
    }

    /**
     * Returns a PlacementRuleGenerator which enforces avoidance of tasks which have the provided
     * type. For example, this could be used to ensure that 'data' nodes are never colocated with
     * 'index' nodes, or that 'data' nodes are never colocated with other 'data' nodes
     * (self-avoidance). Note that this rule is unidirectional; mutual avoidance requires
     * creating separate colocate rules for each direction.
     *
     * Unlike with {@link #createColocate(String, TaskTypeConverter)}, this will NOT throw a
     * {@link StuckDeploymentException} if the task to avoid is missing from the cluster, as this
     * may be expected in certain situations (eg mutual avoidance).
     */
    public static PlacementRuleGenerator createAvoid(
            String typeToColocateWith, TaskTypeConverter typeConverter) {
        return new TaskTypeGenerator(typeToColocateWith, typeConverter, BehaviorType.AVOID);
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
     * The behavior to be used.
     */
    private enum BehaviorType {
        COLOCATE,
        AVOID
    }

    private final String typeToFind;
    private final TaskTypeConverter typeConverter;
    private final BehaviorType behaviorType;

    private TaskTypeGenerator(
            String typeToFind, TaskTypeConverter typeConverter, BehaviorType behaviorType) {
        this.typeToFind = typeToFind;
        this.typeConverter = typeConverter;
        this.behaviorType = behaviorType;
    }

    @Override
    public PlacementRule generate(Collection<TaskInfo> tasks) {
        Set<String> agentsWithMatchingType = new HashSet<>();
        for (TaskInfo task : tasks) {
            if (typeToFind.equals(typeConverter.getTaskType(task))) {
                // Matching task type found. Colocate with it on this agent.
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
                // nothing to colocate with! fall back to picking whatever offer looks good.
                // this is expected when the developer has configured bidirectional rules
                // (A colocates with B + B colocates with A)
                return new PassthroughRule();
            } else {
                return new OrRule(agentRules);
            }
        case AVOID:
            if (agentRules.isEmpty()) {
                // nothing to avoid, but this is expected when avoiding nodes of the same type
                // (self-avoidance), or when the developer has configured bidirectional rules
                // (A avoids B + B avoids A)
                return new PassthroughRule();
            } else {
                return new NotRule(new OrRule(agentRules));
            }
        default:
            throw new IllegalStateException("Unsupported behavior type: " + behaviorType);
        }
    }
}
