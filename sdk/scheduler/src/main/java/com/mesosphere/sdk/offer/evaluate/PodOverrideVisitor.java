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
import java.util.Optional;

/**
 * The PodOverrideVisitor traverses a {@link PodSpec} and replaces task definitions with the override definition
 * specified in the state store.
 */
public class PodOverrideVisitor implements SpecVisitor<VisitorResultCollector.Empty> {
    private final Collection<GoalStateOverride> goalStateOverrides;
    private final VisitorResultCollector<VisitorResultCollector.Empty> collector;
    private final SpecVisitor delegate;

    public PodOverrideVisitor(Collection<GoalStateOverride> goalStateOverrides, SpecVisitor delegate) {
        this.goalStateOverrides = goalStateOverrides;
        this.collector = createVisitorResultCollector();
        this.delegate = delegate;
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
    public Optional<SpecVisitor> getDelegate() {
        return Optional.ofNullable(delegate);
    }

    @Override
    public void compileResultImplementation() { }

    @Override
    public VisitorResultCollector<VisitorResultCollector.Empty> getVisitorResultCollector() {
        return collector;
    }
}
