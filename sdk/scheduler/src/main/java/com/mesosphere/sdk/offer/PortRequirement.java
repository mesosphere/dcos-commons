package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.PortEvaluationStage;
import org.apache.mesos.Protos;

/**
 * A {@link PortRequirement} encapsulates a needed {@link MesosResource} representing a port.
 */
public class PortRequirement extends ResourceRequirement {
    private final String envName;
    private final int port;

    public PortRequirement(Protos.Resource resource, String envName, int port) {
        super(resource);
        this.envName = envName;
        this.port = port;
    }

    public String getEnvName() {
        return envName;
    }

    public int getPort() {
        return port;
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new PortEvaluationStage(getResource(), taskName, getEnvName(), getPort());
    }
}
