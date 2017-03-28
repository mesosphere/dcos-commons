package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.PortEvaluationStage;

import java.util.Optional;

import org.apache.mesos.Protos;

/**
 * A {@link PortRequirement} encapsulates a needed {@link MesosResource} representing a port.
 */
public class PortRequirement extends ResourceRequirement {
    private final String portName;
    private final int port;
    private final Optional<String> customEnvKey;

    public PortRequirement(Protos.Resource resource, String portName, int port, Optional<String> customEnvKey) {
        super(resource);
        this.portName = portName;
        this.port = port;
        this.customEnvKey = customEnvKey;
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

    @Override
    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new PortEvaluationStage(getResource(), taskName, getPortName(), getPort(), getCustomEnvKey());
    }
}
