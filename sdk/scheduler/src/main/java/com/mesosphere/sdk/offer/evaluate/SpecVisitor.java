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
public interface SpecVisitor<T> {

    default PodInstanceRequirement visit(PodInstanceRequirement podInstanceRequirement) throws SpecVisitorException {
        PodInstanceRequirement visited = visitImplementation(podInstanceRequirement);

        if (getDelegate().isPresent()) {
            visited = getDelegate().get().visit(visited);
        }

        return visited;
    }

    PodInstanceRequirement visitImplementation(
            PodInstanceRequirement podInstanceRequirement) throws SpecVisitorException;

    default PodSpec visit(PodSpec podSpec) throws SpecVisitorException {
        PodSpec visited = visitImplementation(podSpec);

        if (getDelegate().isPresent()) {
            visited = getDelegate().get().visit(visited);
        }

        return visited;
    }

    PodSpec visitImplementation(PodSpec podSpec) throws SpecVisitorException;

    default TaskSpec visit(TaskSpec taskSpec) throws SpecVisitorException {
        TaskSpec visited = visitImplementation(taskSpec);

        if (getDelegate().isPresent()) {
            visited = getDelegate().get().visit(visited);
        }

        return visited;
    }

    TaskSpec visitImplementation(TaskSpec taskSpec) throws SpecVisitorException;

    default ResourceSpec visit(ResourceSpec resourceSpec) throws SpecVisitorException {
        ResourceSpec visited = visitImplementation(resourceSpec);

        if (getDelegate().isPresent()) {
            visited = getDelegate().get().visit(visited);
        }

        return visited;
    }

    ResourceSpec visitImplementation(ResourceSpec resourceSpec) throws SpecVisitorException;

    default VolumeSpec visit(VolumeSpec volumeSpec) throws SpecVisitorException {
        VolumeSpec visited = visitImplementation(volumeSpec);

        if (getDelegate().isPresent()) {
            visited = getDelegate().get().visit(visited);
        }

        return visited;
    }

    VolumeSpec visitImplementation(VolumeSpec volumeSpec) throws SpecVisitorException;

    default PortSpec visit(PortSpec portSpec) throws SpecVisitorException {
        PortSpec visited = visitImplementation(portSpec);

        if (getDelegate().isPresent()) {
            visited = getDelegate().get().visit(visited);
        }

        return visited;
    }

    PortSpec visitImplementation(PortSpec portSpec) throws SpecVisitorException;

    default PodSpec finalizeVisit(PodSpec podSpec) throws SpecVisitorException {
        PodSpec finalized = finalizeImplementation(podSpec);

        if (getDelegate().isPresent()) {
            finalized = getDelegate().get().finalizeVisit(finalized);
        }

        return finalized;
    }

    NamedVIPSpec visitImplementation(NamedVIPSpec namedVIPSpec) throws SpecVisitorException;

    default NamedVIPSpec visit(NamedVIPSpec namedVIPSpec) throws SpecVisitorException {
        NamedVIPSpec visited = visitImplementation(namedVIPSpec);

        if (getDelegate().isPresent()) {
            visited = getDelegate().get().visit(visited);
        }

        return visited;
    }

    default NamedVIPSpec finalizeImplementation(NamedVIPSpec namedVIPSpec) {
        return namedVIPSpec;
    }

    default NamedVIPSpec finalizeVisit(NamedVIPSpec namedVIPSpec) throws SpecVisitorException {
        NamedVIPSpec finalized = finalizeImplementation(namedVIPSpec);

        if (getDelegate().isPresent()) {
            finalized = getDelegate().get().finalizeVisit(finalized);
        }

        return finalized;
    }

    default PodSpec finalizeImplementation(PodSpec podSpec) throws SpecVisitorException {
        return podSpec;
    }

    default TaskSpec finalizeVisit(TaskSpec taskSpec) throws SpecVisitorException {
        TaskSpec finalized = finalizeImplementation(taskSpec);

        if (getDelegate().isPresent()) {
            finalized = getDelegate().get().finalizeVisit(finalized);
        }

        return finalized;
    }

    default TaskSpec finalizeImplementation(TaskSpec taskSpec) throws SpecVisitorException {
        return taskSpec;
    }

    default ResourceSpec finalizeVisit(ResourceSpec resourceSpec) throws SpecVisitorException {
        ResourceSpec finalized = finalizeImplementation(resourceSpec);

        if (getDelegate().isPresent()) {
            finalized = getDelegate().get().finalizeVisit(finalized);
        }

        return finalized;
    }

    default ResourceSpec finalizeImplementation(ResourceSpec resourceSpec) throws SpecVisitorException {
        return resourceSpec;
    }

    default VolumeSpec finalizeVisit(VolumeSpec volumeSpec) throws SpecVisitorException {
        VolumeSpec finalized = finalizeImplementation(volumeSpec);

        if (getDelegate().isPresent()) {
            finalized = getDelegate().get().finalizeVisit(finalized);
        }

        return finalized;
    }

    default VolumeSpec finalizeImplementation(VolumeSpec volumeSpec) throws SpecVisitorException {
        return volumeSpec;
    }

    default PortSpec finalizeVisit(PortSpec portSpec) throws SpecVisitorException {
        PortSpec finalized = finalizeImplementation(portSpec);

        if (getDelegate().isPresent()) {
            finalized = getDelegate().get().finalizeVisit(finalized);
        }

        return finalized;
    }

    default PortSpec finalizeImplementation(PortSpec portSpec) throws SpecVisitorException {
        return portSpec;
    }

    Optional<SpecVisitor> getDelegate();

    default void compileResult() {
        compileResultImplementation();

        Optional<SpecVisitor> delegate = getDelegate();
        if (delegate.isPresent()) {
            delegate.get().compileResult();
        }
    }

    void compileResultImplementation();

    default ResourceSpec withResource(ResourceSpec resourceSpec, Protos.Resource.Builder resource) {
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

    default VolumeSpec withResource(VolumeSpec volumeSpec, Protos.Resource.Builder resource) {
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

    default PortSpec withResource(PortSpec portSpec, Protos.Resource.Builder resource) {
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

    default VisitorResultCollector<T> createVisitorResultCollector() {
        return new VisitorResultCollector<T>() {
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

    VisitorResultCollector<T> getVisitorResultCollector();
}
