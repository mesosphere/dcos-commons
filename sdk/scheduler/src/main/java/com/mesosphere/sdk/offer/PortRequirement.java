package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.PortEvaluationStage;
import org.apache.mesos.Protos;

/**
 * A {@link PortRequirement} encapsulates a needed {@link MesosResource} representing a port.
 */
public class PortRequirement extends ResourceRequirement {
    private final String portName;
    private final int port;

    public PortRequirement(Protos.Resource resource, String portName, int port) {
        super(resource);
        this.portName = portName;
        this.port = port;
    }

    public String getPortName() {
        return portName;
    }

    public int getPort() {
        return port;
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new PortEvaluationStage(getResource(), taskName, getPortName(), getPort());
    }
}
