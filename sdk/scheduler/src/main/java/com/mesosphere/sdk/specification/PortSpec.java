package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.evaluate.SpecVisitor;
import com.mesosphere.sdk.offer.evaluate.SpecVisitorException;
import org.apache.mesos.Protos;

import java.util.Collection;

/**
 * This interface represents a single port, with associated environment name.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public interface PortSpec extends ResourceSpec {
    /**
     * Returns a copy of the provided {@link DefaultPortSpec} which has been updated to have the provided {@code value}.
     */
    @JsonIgnore
    static PortSpec withValue(PortSpec portSpec, Protos.Value value) {
        return new DefaultPortSpec(
                value,
                portSpec.getRole(),
                portSpec.getPreReservedRole(),
                portSpec.getPrincipal(),
                portSpec.getEnvKey().get(),
                portSpec.getPortName(),
                portSpec.getVisibility(),
                portSpec.getNetworkNames());
    }

    @JsonProperty("port-name")
    String getPortName();

    @JsonProperty("visibility")
    Protos.DiscoveryInfo.Visibility getVisibility();

    @JsonProperty("network-names")
    Collection<String> getNetworkNames();

    @JsonIgnore
    long getPort();

    default boolean requiresHostPorts() {
        return getNetworkNames().isEmpty() ||
                getNetworkNames().stream().filter(DcosConstants::networkSupportsPortMapping).count() > 0;
    }

    default void accept(SpecVisitor specVisitor) throws SpecVisitorException {
        specVisitor.visit(this);
        specVisitor.finalizeVisit(this);
    }
}
