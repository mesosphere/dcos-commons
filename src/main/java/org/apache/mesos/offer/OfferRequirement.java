package org.apache.mesos.offer;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

/**
 * An OfferRequirement encapsulates the needed resources an Offer must have.
 * In general these are Resource requirements like it must have a certain amount of
 * cpu, memory, and disk.  Additionally it has two modes regarding expectations around
 * Persistent Volumes.  In the CREATE mode it anticipates that the Scheduler will be
 * creating the required volume, so a Volume with a particular persistence id is not
 * required to be already present in an Offer.  In the EXISTING mode, we expect that
 * an Offer will already have the indicated persistence ID.
 */
public class OfferRequirement {
    private final Collection<TaskRequirement> taskRequirements;
    private final Optional<ExecutorRequirement> executorRequirementOptional;
    private final Optional<PlacementRuleGenerator> placementRuleGeneratorOptional;

    /**
     * Creates a new OfferRequirement.
     *
     * @param taskInfos the 'draft' {@link TaskInfo}s from which task requirements should be generated
     * @param executorInfoOptional the executor from which an executor requirement should be
     *     generated, if any
     * @param placementRuleGeneratorOptional the placement constraints which should be applied to the
     *     tasks, if any
     * @throws InvalidRequirementException if task or executor requirements could not be generated
     *     from the provided information
     */
    public OfferRequirement(
            Collection<TaskInfo> taskInfos,
            Optional<ExecutorInfo> executorInfoOptional,
            Optional<PlacementRuleGenerator> placementRuleGeneratorOptional)
                    throws InvalidRequirementException {
        this.taskRequirements = getTaskRequirementsInternal(taskInfos);
        this.executorRequirementOptional = executorInfoOptional.isPresent()
                ? Optional.of(ExecutorRequirement.create(executorInfoOptional.get()))
                : Optional.empty();
        this.placementRuleGeneratorOptional = placementRuleGeneratorOptional;
    }

    /**
     * Creates a new OfferRequirement with empty executor requirement and empty placement constraints.
     *
     * @see #OfferRequirement(Collection, Optional, Optional)
     */
    public OfferRequirement(Collection<TaskInfo> taskInfos) throws InvalidRequirementException {
        this(taskInfos, Optional.empty(), Optional.empty());
    }

    /**
     * Creates a new OfferRequirement with provided executor requirement and empty placement
     * constraints.
     *
     * @see #OfferRequirement(Collection, Optional, Optional)
     */
    public OfferRequirement(
            Collection<TaskInfo> taskInfos, Optional<ExecutorInfo> executorInfoOptional)
                    throws InvalidRequirementException {
        this(taskInfos, executorInfoOptional, Optional.empty());
    }

    public Collection<TaskRequirement> getTaskRequirements() {
        return taskRequirements;
    }

    public Optional<ExecutorRequirement> getExecutorRequirement() {
        return executorRequirementOptional;
    }

    public Optional<PlacementRuleGenerator> getPlacementRuleGenerator() {
        return placementRuleGeneratorOptional;
    }

    public Collection<String> getResourceIds() {
        Collection<String> resourceIds = new ArrayList<String>();

        for (TaskRequirement taskReq : taskRequirements) {
            resourceIds.addAll(taskReq.getResourceIds());
        }

        if (executorRequirementOptional.isPresent())    {
            resourceIds.addAll(executorRequirementOptional.get().getResourceIds());
        }

        return resourceIds;
    }

    public Collection<String> getPersistenceIds() {
        Collection<String> persistenceIds = new ArrayList<String>();

        for (TaskRequirement taskReq : taskRequirements) {
            persistenceIds.addAll(taskReq.getPersistenceIds());
        }

        if (executorRequirementOptional.isPresent())    {
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
