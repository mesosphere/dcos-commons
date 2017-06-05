package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.PortEvaluationStage;

import java.util.Collection;
import java.util.Optional;

import org.apache.mesos.Protos;

/**
 * A {@link PortRequirement} encapsulates a needed {@link MesosResource} representing a port.
 */
public class PortRequirement extends ResourceRequirement {
    private final String portName;
    private final int port;
    private final Optional<String> customEnvKey;
    private final boolean useHostPorts;

    public PortRequirement(Protos.Resource resource, String portName, int port, Optional<String> customEnvKey,
                           Collection<String> networkNames) {
        super(resource);
        this.portName = portName;
        this.port = port;
        this.customEnvKey = customEnvKey;
        this.useHostPorts = requireHostPorts(networkNames);
    }

    public String getPortName() {
        return portName;
    }

    public int getPort() {
        return port;
    }

    public Optional<String> getCustomEnvKey() {
        return customEnvKey;
    }

    public boolean usingHostPorts() {
        return useHostPorts;
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new PortEvaluationStage(
                getResource(),
                taskName,
                getPortName(),
                getPort(),
                getCustomEnvKey(),
                usingHostPorts());
    }

    private boolean requireHostPorts(Collection<String> networkNames) {
        if (networkNames.isEmpty()) {  // no network names, must be on host network and use the host IP
            return true;
        } else {
            return networkNames.stream()
                    .filter(DcosConstants::networkSupportsPortMapping)
                    .count() > 0;
        }

        //for (String networkName : networkNames) {
        //    if (DcosConstants.networkSupportsPortMapping(networkName)) {
        //        return true;
        //    }
        //}
        //return false;
    }
}
