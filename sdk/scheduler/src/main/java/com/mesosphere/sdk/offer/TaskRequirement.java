package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.state.StateStore;

import java.util.Collection;

/**
 * A TaskRequirement encapsulates the needed resources a Task must have.
 */
public class TaskRequirement {
    private final TaskInfo taskInfo;
    private final Collection<ResourceRequirement> resourceRequirements;
    private Collection<DynamicPortRequirement> dynamicPortRequirements;
    private Collection<NamedVIPPortRequirement> namedVIPPortRequirements;

    public TaskRequirement(TaskInfo unverifiedTaskInfo) throws InvalidRequirementException {
        validateTaskInfo(unverifiedTaskInfo);
        // TaskID is always overwritten with a new UUID, even if already present:
        this.taskInfo = TaskInfo.newBuilder(unverifiedTaskInfo)
                .setTaskId(CommonTaskUtils.toTaskId(unverifiedTaskInfo.getName()))
                .build();
        this.resourceRequirements =
                RequirementUtils.getResourceRequirements(taskInfo.getResourcesList());
        this.dynamicPortRequirements =
                RequirementUtils.getDynamicPortRequirements(taskInfo.getResourcesList());
        this.namedVIPPortRequirements =
                RequirementUtils.getNamedVIPPortRequirements(taskInfo.getResourcesList());
    }

    public TaskInfo getTaskInfo() {
        return taskInfo;
    }

    public Collection<ResourceRequirement> getResourceRequirements() {
        return resourceRequirements;
    }

    public Collection<DynamicPortRequirement> getDynamicPortRequirements() {
        return dynamicPortRequirements;
    }

    public Collection<NamedVIPPortRequirement> getNamedVIPPortRequirements() {
        return namedVIPPortRequirements;
    }

    public Collection<String> getResourceIds() {
        return RequirementUtils.getResourceIds(getResourceRequirements());
    }

    public Collection<String> getPersistenceIds() {
        return RequirementUtils.getPersistenceIds(getResourceRequirements());
    }

    /**
     * Checks that the TaskInfo is valid at the point of requirement construction, making it
     * easier for the framework developer to trace problems in their implementation. These checks
     * reflect requirements enforced elsewhere, eg in {@link StateStore}.
     */
    private static void validateTaskInfo(TaskInfo taskInfo)
            throws InvalidRequirementException {
        if (!taskInfo.hasName() || StringUtils.isEmpty(taskInfo.getName())) {
            throw new InvalidRequirementException(String.format(
                    "TaskInfo must have a name: %s", taskInfo));
        }

        if (taskInfo.hasTaskId()
                && !StringUtils.isEmpty(taskInfo.getTaskId().getValue())) {
            // Task ID may be included if this is replacing an existing task. In that case, we still
            // perform a sanity check to ensure that the original Task ID was formatted correctly.
            // We must allow Task ID to be present but empty because it is a required proto field.
            String taskName;
            try {
                taskName = CommonTaskUtils.toTaskName(taskInfo.getTaskId());
            } catch (TaskException e) {
                throw new InvalidRequirementException(String.format(
                        "When non-empty, TaskInfo.id must be a valid ID. "
                        + "Set to an empty string or leave existing valid value. %s %s",
                        taskInfo, e));
            }
            if (!taskName.equals(taskInfo.getName())) {
                throw new InvalidRequirementException(String.format(
                        "When non-empty, TaskInfo.id must align with TaskInfo.name. Use "
                        + "TaskUtils.toTaskId(): %s", taskInfo));
            }
        }

        if (taskInfo.hasExecutor()) {
            throw new InvalidRequirementException(String.format(
                    "TaskInfo must not contain ExecutorInfo. "
                    + "Use ExecutorRequirement for any Executor requirements: %s", taskInfo));
        }

        try {
            CommonTaskUtils.getType(taskInfo);
        } catch (TaskException e) {
            throw new InvalidRequirementException(e);
        }

        try {
            CommonTaskUtils.getIndex(taskInfo);
        } catch (TaskException e) {
            throw new InvalidRequirementException(e);
        }
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
