package org.apache.mesos.offer;

import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.executor.ExecutorTaskException;
import org.apache.mesos.executor.ExecutorUtils;

/**
 * An ExecutorRequirement encapsulates the needed resources an Executor must
 * have.
 */
public class ExecutorRequirement {
    private ExecutorInfo executorInfo;
    private Collection<ResourceRequirement> resourceRequirements;

    public ExecutorRequirement(ExecutorInfo unverifiedExecutorInfo)
            throws InvalidRequirementException {
        validateExecutorInfo(unverifiedExecutorInfo);
        // ExecutorID is always overwritten with a new UUID, even if already present:
        this.executorInfo = ExecutorInfo.newBuilder(unverifiedExecutorInfo)
                .setExecutorId(ExecutorUtils.toExecutorId(unverifiedExecutorInfo.getName()))
                .build();
        this.resourceRequirements =
                RequirementUtils.getResourceRequirements(this.executorInfo.getResourcesList());
    }

    public ExecutorInfo getExecutorInfo() {
        return executorInfo;
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

    public boolean desiresResources() {
        for (ResourceRequirement resReq : resourceRequirements) {
            if (resReq.reservesResource()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks that the ExecutorInfo is valid at the point of requirement construction, making it
     * easier for the framework developer to trace problems in their implementation. These checks
     * reflect requirements enforced elsewhere, eg in {@link StateStore}.
     *
     * @return a validated ExecutorInfo
     * @throws InvalidRequirementException if the ExecutorInfo is malformed
     */
    private static void validateExecutorInfo(ExecutorInfo executorInfo)
            throws InvalidRequirementException {
        if (!executorInfo.hasName() || StringUtils.isEmpty(executorInfo.getName())) {
            throw new InvalidRequirementException(String.format(
                    "ExecutorInfo must have a name: %s", executorInfo));
        }
        if (executorInfo.hasExecutorId()
                && !StringUtils.isEmpty(executorInfo.getExecutorId().getValue())) {
            // Executor ID may be included if this is replacing an existing task. In that case, we
            // still perform a sanity check to ensure that the original Task ID was formatted
            // correctly. We must allow Executor ID to be present but empty because it is a required
            // proto field.
            String executorName;
            try {
                executorName = ExecutorUtils.toExecutorName(executorInfo.getExecutorId());
            } catch (ExecutorTaskException e) {
                throw new InvalidRequirementException(String.format(
                        "When non-empty, ExecutorInfo.id must be a valid ID. "
                        + "Set to an empty string or leave existing valid value. %s %s",
                        executorInfo, e));
            }
            if (!executorName.equals(executorInfo.getName())) {
                throw new InvalidRequirementException(String.format(
                        "When non-empty, ExecInfo.id must align with ExecInfo.name. Use "
                        + "ExecutorUtils.toExecutorId(): %s", executorInfo));
            }
        }
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
