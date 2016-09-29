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
    private final Collection<DynamicPortRequirement> dynamicPortRequirements;
    private ExecutorInfo executorInfo;
    private Collection<ResourceRequirement> resourceRequirements;

    /**
     * This method generates one of two possible Executor requirements.  In the first case, if an empty ExecutorID is
     * presented in the ExecutorInfo an ExecutorRequirement representing a need for a new Executor is returned.  In the
     * second case, if an ExecutorInfo with a valid name is presented a requirement indicating use of an already running
     * Executor is generated.
     * @param executorInfo is the ExecutorInfo indicate what requirement should be generated.
     * @return an ExecutorRequirement to be used to evaluate Offers by the OfferEvaluator
     * @throws InvalidRequirementException when a malformed ExecutorInfo is presented indicating an invalid
     * ExecutorRequirement.
     */
    public static ExecutorRequirement create(ExecutorInfo executorInfo)
        throws InvalidRequirementException {
        if (executorInfo.getExecutorId().getValue().isEmpty()) {
            return createExecutorRequirement(executorInfo);
        } else {
            return getExistingExecutorRequirement(executorInfo);
        }
    }

    private static ExecutorRequirement createExecutorRequirement(ExecutorInfo executorInfo)
            throws InvalidRequirementException{
        return new ExecutorRequirement(executorInfo);
    }

    private static ExecutorRequirement getExistingExecutorRequirement(ExecutorInfo executorInfo)
        throws InvalidRequirementException {
        ExecutorRequirement executorRequirement = new ExecutorRequirement(executorInfo);

        if (executorRequirement.desiresResources()) {
            throw new InvalidRequirementException("When using an existing Executor, no new resources may be required.");
        } else {
            return executorRequirement;
        }
    }

    private ExecutorRequirement(ExecutorInfo executorInfo)
            throws InvalidRequirementException {
        validateExecutorInfo(executorInfo);
        this.executorInfo = executorInfo;
        this.resourceRequirements =
                RequirementUtils.getResourceRequirements(executorInfo.getResourcesList());
        this.dynamicPortRequirements =
                RequirementUtils.getDynamicPortRequirements(executorInfo.getResourcesList());
    }

    public ExecutorInfo getExecutorInfo() {
        return executorInfo;
    }

    public Collection<ResourceRequirement> getResourceRequirements() {
        return resourceRequirements;
    }

    public Collection<DynamicPortRequirement> getDynamicPortRequirements() {
        return dynamicPortRequirements;
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
            // still perform a sanity check to ensure that the original Executor ID was formatted
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
                        "When non-empty, ExecutorInfo.id must align with ExecutorInfo.name. Use "
                        + "ExecutorUtils.toExecutorId(): %s", executorInfo));
            }
        }
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
