package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;

/**
 * The NullVisitor passes on each spec it visits to its delegate {@link SpecVisitor} and does not return a result. It is
 * useful for cases where a certain visitor type is only relevant to a certain type of configuration, such as with the
 * default executor, where custom-executor-based environments do not need to do a separate pass to account for resources
 * consumed by the executor.
 */
public class NullVisitor extends SpecVisitor<VisitorResultCollector.Empty> {
    public NullVisitor(SpecVisitor delegate) {
        super(delegate);
    }

    @Override
    public PodInstanceRequirement visitImplementation(PodInstanceRequirement podInstanceRequirement) {
        return podInstanceRequirement;
    }

    @Override
    public PodSpec visitImplementation(PodSpec podSpec) {
        return podSpec;
    }

    @Override
    public TaskSpec visitImplementation(TaskSpec taskSpec) {
        return taskSpec;
    }

    @Override
    public ResourceSpec visitImplementation(ResourceSpec resourceSpec) {
        return resourceSpec;
    }

    @Override
    public VolumeSpec visitImplementation(VolumeSpec volumeSpec) {
        return volumeSpec;
    }

    @Override
    public PortSpec visitImplementation(PortSpec portSpec) {
        return portSpec;
    }

    @Override
    public NamedVIPSpec visitImplementation(NamedVIPSpec namedVIPSpec) throws SpecVisitorException {
        return namedVIPSpec;
    }

    @Override
    public void compileResultImplementation() { }

    @Override
    public VisitorResultCollector<VisitorResultCollector.Empty> getVisitorResultCollector() {
        return new VisitorResultCollector<VisitorResultCollector.Empty>() {
            @Override
            public void setResult(Empty result) {
                throw new IllegalAccessError("The Empty result collector may not be set");
            }

            @Override
            public Empty getResult() {
                throw new IllegalAccessError("The Empty result collector may not be retrieved from");
            }
        };
    }
}
