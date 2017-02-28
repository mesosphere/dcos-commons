package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.PortEvaluationStage;
import org.apache.mesos.Protos;

/**
 * A {@link PortRangeRequirement} encapsulates a needed {@link MesosResource} representing a port range.
 */
public class PortRangeRequirement extends ResourceRequirement {
    private final String envKey;
    private final int begin;
    private final int end;

    public PortRangeRequirement(Protos.Resource resource, String envKey, int begin, int end) {
        super(resource);
        this.envKey = envKey;
        this.begin = begin;
        this.end = end;
    }

    public String getEnvKey() {
        return envKey;
    }

    public int getBegin() {
        return begin;
    }

    public int getEnd() {
        return end;
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new PortEvaluationStage(getResource(), taskName, getEnvKey(), getBegin(), getEnd());
    }
}
