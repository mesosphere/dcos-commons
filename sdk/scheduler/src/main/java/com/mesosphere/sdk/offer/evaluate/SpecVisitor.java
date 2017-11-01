package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.Optional;

/**
 * The SpecVisitor knows the structure of a {@link PodSpec} and all of its contained specs, and executes some action at
 * each type of spec. SpecVisitors are designed to have delegates, and so various actions can be chained together and
 * cumulatively executed by virtue of one SpecVisitor passing on a modified version of the spec it is visiting to its
 * delegate. For this reason, the visit methods are supplied with default implementations that handle this logic, and
 * the actual visit implementation is specified in a template method.
 * @param <T> The type of the result of the spec traversal
 */
public abstract class SpecVisitor<T> {
    private final SpecVisitor delegate;
    private final VisitorResultCollector<T> collector;

    public SpecVisitor(SpecVisitor delegate) {
        this.delegate = delegate;
        collector = new VisitorResultCollector<T>() {
            private T result;

            @Override
            public void setResult(T result) {
                this.result = result;
            }

            @Override
            public T getResult() {
                return result;
            }
        };
    }

    public PodInstanceRequirement visit(PodInstanceRequirement podInstanceRequirement) throws SpecVisitorException {
        PodInstanceRequirement visited = visitImplementation(podInstanceRequirement);

        if (getDelegate().isPresent()) {
            visited = getDelegate().get().visit(visited);
        }

        return visited;
    }

    abstract PodInstanceRequirement visitImplementation(
            PodInstanceRequirement podInstanceRequirement) throws SpecVisitorException;

    public PodSpec visit(PodSpec podSpec) throws SpecVisitorException {
        PodSpec visited = visitImplementation(podSpec);

        if (getDelegate().isPresent()) {
            visited = getDelegate().get().visit(visited);
        }

        return visited;
    }

    abstract PodSpec visitImplementation(PodSpec podSpec) throws SpecVisitorException;

    public TaskSpec visit(TaskSpec taskSpec) throws SpecVisitorException {
        TaskSpec visited = visitImplementation(taskSpec);

        if (getDelegate().isPresent()) {
            visited = getDelegate().get().visit(visited);
        }

        return visited;
    }

    abstract TaskSpec visitImplementation(TaskSpec taskSpec) throws SpecVisitorException;

    public ResourceSpec visit(ResourceSpec resourceSpec) throws SpecVisitorException {
        ResourceSpec visited = visitImplementation(resourceSpec);

        if (getDelegate().isPresent()) {
            visited = getDelegate().get().visit(visited);
        }

        return visited;
    }

    abstract ResourceSpec visitImplementation(ResourceSpec resourceSpec) throws SpecVisitorException;

    public VolumeSpec visit(VolumeSpec volumeSpec) throws SpecVisitorException {
        VolumeSpec visited = visitImplementation(volumeSpec);

        if (getDelegate().isPresent()) {
            visited = getDelegate().get().visit(visited);
        }

        return visited;
    }

    abstract VolumeSpec visitImplementation(VolumeSpec volumeSpec) throws SpecVisitorException;

    public PortSpec visit(PortSpec portSpec) throws SpecVisitorException {
        PortSpec visited = visitImplementation(portSpec);

        if (getDelegate().isPresent()) {
            visited = getDelegate().get().visit(visited);
        }

        return visited;
    }

    abstract PortSpec visitImplementation(PortSpec portSpec) throws SpecVisitorException;

    public PodSpec finalizeVisit(PodSpec podSpec) throws SpecVisitorException {
        PodSpec finalized = finalizeImplementation(podSpec);

        if (getDelegate().isPresent()) {
            finalized = getDelegate().get().finalizeVisit(finalized);
        }

        return finalized;
    }

    abstract NamedVIPSpec visitImplementation(NamedVIPSpec namedVIPSpec) throws SpecVisitorException;

    public NamedVIPSpec visit(NamedVIPSpec namedVIPSpec) throws SpecVisitorException {
        NamedVIPSpec visited = visitImplementation(namedVIPSpec);

        if (getDelegate().isPresent()) {
            visited = getDelegate().get().visit(visited);
        }

        return visited;
    }

    NamedVIPSpec finalizeImplementation(NamedVIPSpec namedVIPSpec) {
        return namedVIPSpec;
    }

    public NamedVIPSpec finalizeVisit(NamedVIPSpec namedVIPSpec) throws SpecVisitorException {
        NamedVIPSpec finalized = finalizeImplementation(namedVIPSpec);

        if (getDelegate().isPresent()) {
            finalized = getDelegate().get().finalizeVisit(finalized);
        }

        return finalized;
    }

    PodSpec finalizeImplementation(PodSpec podSpec) throws SpecVisitorException {
        return podSpec;
    }

    public TaskSpec finalizeVisit(TaskSpec taskSpec) throws SpecVisitorException {
        TaskSpec finalized = finalizeImplementation(taskSpec);

        if (getDelegate().isPresent()) {
            finalized = getDelegate().get().finalizeVisit(finalized);
        }

        return finalized;
    }

    TaskSpec finalizeImplementation(TaskSpec taskSpec) throws SpecVisitorException {
        return taskSpec;
    }

    public ResourceSpec finalizeVisit(ResourceSpec resourceSpec) throws SpecVisitorException {
        ResourceSpec finalized = finalizeImplementation(resourceSpec);

        if (getDelegate().isPresent()) {
            finalized = getDelegate().get().finalizeVisit(finalized);
        }

        return finalized;
    }

    ResourceSpec finalizeImplementation(ResourceSpec resourceSpec) throws SpecVisitorException {
        return resourceSpec;
    }

    public VolumeSpec finalizeVisit(VolumeSpec volumeSpec) throws SpecVisitorException {
        VolumeSpec finalized = finalizeImplementation(volumeSpec);

        if (getDelegate().isPresent()) {
            finalized = getDelegate().get().finalizeVisit(finalized);
        }

        return finalized;
    }

    VolumeSpec finalizeImplementation(VolumeSpec volumeSpec) throws SpecVisitorException {
        return volumeSpec;
    }

    public PortSpec finalizeVisit(PortSpec portSpec) throws SpecVisitorException {
        PortSpec finalized = finalizeImplementation(portSpec);

        if (getDelegate().isPresent()) {
            finalized = getDelegate().get().finalizeVisit(finalized);
        }

        return finalized;
    }

    PortSpec finalizeImplementation(PortSpec portSpec) throws SpecVisitorException {
        return portSpec;
    }

    Optional<SpecVisitor> getDelegate() {
        return Optional.ofNullable(delegate);
    }

    public void compileResult() {
        compileResultImplementation();

        Optional<SpecVisitor> delegate = getDelegate();
        if (delegate.isPresent()) {
            delegate.get().compileResult();
        }
    }

    abstract void compileResultImplementation();

    VisitorResultCollector<T> getVisitorResultCollector() {
        return collector;
    }

    ResourceSpec withResource(ResourceSpec resourceSpec, Protos.Resource.Builder resource) {
        return new ResourceSpec() {
            @Override
            public Protos.Value getValue() {
                return resourceSpec.getValue();
            }

            @Override
            public String getName() {
                return resourceSpec.getName();
            }

            @Override
            public String getRole() {
                return resourceSpec.getRole();
            }

            @Override
            public String getPreReservedRole() {
                return resourceSpec.getPreReservedRole();
            }

            @Override
            public String getPrincipal() {
                return resourceSpec.getPrincipal();
            }

            @Override
            public ResourceSpec getResourceSpec() {
                return this;
            }

            @Override
            public Protos.Resource.Builder getResource() {
                return resource;
            }

            @Override
            public String toString() {
                return resourceSpec.toString();
            }
        };
    }

    VolumeSpec withResource(VolumeSpec volumeSpec, Protos.Resource.Builder resource) {
        return new VolumeSpec() {
            @Override
            public Type getType() {
                return volumeSpec.getType();
            }

            @Override
            public String getContainerPath() {
                return volumeSpec.getContainerPath();
            }

            @Override
            public Protos.Value getValue() {
                return volumeSpec.getValue();
            }

            @Override
            public String getName() {
                return volumeSpec.getName();
            }

            @Override
            public String getRole() {
                return volumeSpec.getRole();
            }

            @Override
            public String getPreReservedRole() {
                return volumeSpec.getPreReservedRole();
            }

            @Override
            public String getPrincipal() {
                return volumeSpec.getPrincipal();
            }

            @Override
            public ResourceSpec getResourceSpec() {
                return this;
            }

            @Override
            public VolumeSpec getVolumeSpec() {
                return this;
            }

            @Override
            public Protos.Resource.Builder getResource() {
                return resource;
            }

            @Override
            public String toString() {
                return volumeSpec.toString();
            }
        };
    }

    PortSpec withResource(PortSpec portSpec, Protos.Resource.Builder resource) {
        return new PortSpec() {
            @Override
            public String getPortName() {
                return portSpec.getPortName();
            }

            @Override
            public Protos.DiscoveryInfo.Visibility getVisibility() {
                return portSpec.getVisibility();
            }

            @Override
            public Collection<String> getNetworkNames() {
                return portSpec.getNetworkNames();
            }

            @Override
            public long getPort() {
                return portSpec.getPort();
            }

            @Override
            public Protos.Value getValue() {
                return portSpec.getValue();
            }

            @Override
            public String getName() {
                return portSpec.getName();
            }

            @Override
            public String getRole() {
                return portSpec.getRole();
            }

            @Override
            public String getPreReservedRole() {
                return portSpec.getPreReservedRole();
            }

            @Override
            public String getPrincipal() {
                return portSpec.getPrincipal();
            }

            @Override
            public ResourceSpec getResourceSpec() {
                return this;
            }

            @Override
            public Protos.Resource.Builder getResource() {
                return resource;
            }

            @Override
            public String toString() {
                return portSpec.toString();
            }
        };
    }
}
