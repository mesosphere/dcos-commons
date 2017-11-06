package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;

import java.util.ArrayList;
import java.util.Collection;

/**
 * The NullVisitor passes on each spec it visits to its delegate {@link SpecVisitor} and does not return a result. It is
 * useful for cases where a certain visitor type is only relevant to a certain type of configuration, such as with the
 * default executor, where custom-executor-based environments do not need to do a separate pass to account for resources
 * consumed by the executor.
 * @param <T> the type of the result
 */
public class NullVisitor<T> extends SpecVisitor<T> {
    public NullVisitor(SpecVisitor delegate) {
        super(delegate);
    }

    @Override
    public PodInstanceRequirement visitImplementation(
            PodInstanceRequirement podInstanceRequirement) throws SpecVisitorException {
        return podInstanceRequirement;
    }

    @Override
    public PodSpec visitImplementation(PodSpec podSpec) throws SpecVisitorException {
        return podSpec;
    }

    @Override
    public TaskSpec visitImplementation(TaskSpec taskSpec) throws SpecVisitorException {
        return taskSpec;
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
    Collection<T> getResultImplementation() {
        return new ArrayList<>();
    }
}
