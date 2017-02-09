package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.PortEvaluationStage;
import org.apache.mesos.Protos;

/**
 * A {@link PortRequirement} encapsulates a needed {@link MesosResource} representing a port.
 */
public class PortRequirement extends ResourceRequirement {
    private final String envKey;
    private final int port;

    public PortRequirement(Protos.Resource resource, String envKey, int port) {
        super(resource);
        this.envKey = envKey;
        this.port = port;
    }

    public String getEnvKey() {
        return envKey;
    }

    public int getPort() {
        return port;
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new PortEvaluationStage(getResource(), taskName, getEnvKey(), getPort());
    }
}
