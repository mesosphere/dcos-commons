package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import com.mesosphere.sdk.state.GoalStateOverride;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * The PodOverrideVisitor traverses a {@link PodSpec} and replaces task definitions with the override definition
 * specified in the state store.
 */
public class PodOverrideVisitor extends SpecVisitor<EvaluationOutcome> {
    private final Collection<GoalStateOverride> goalStateOverrides;

    public PodOverrideVisitor(Collection<GoalStateOverride> goalStateOverrides, SpecVisitor delegate) {
        super(delegate);
        this.goalStateOverrides = goalStateOverrides;
    }

    @Override
    public PodInstanceRequirement visitImplementation(PodInstanceRequirement podInstanceRequirement) {
        return podInstanceRequirement;
    }

    @Override
    public PodSpec visitImplementation(PodSpec podSpec) throws SpecVisitorException {
        return podSpec;
    }

    @Override
    public TaskSpec visitImplementation(TaskSpec taskSpec) throws SpecVisitorException {
        return null;
    }

    @Override
    public ResourceSpec visitImplementation(ResourceSpec resourceSpec) throws SpecVisitorException {
        return resourceSpec;
    }

    @Override
    public VolumeSpec visitImplementation(VolumeSpec volumeSpec) throws SpecVisitorException {
        return volumeSpec;
    }

    @Override
    public PortSpec visitImplementation(PortSpec portSpec) throws SpecVisitorException {
        return portSpec;
    }

    @Override
    public NamedVIPSpec visitImplementation(NamedVIPSpec namedVIPSpec) throws SpecVisitorException {
        return namedVIPSpec;
    }

    @Override
    public Collection<EvaluationOutcome> getResultImplementation() {
        return Collections.emptyList();
    }
}
