package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.PortEvaluationStage;

import java.util.Optional;

import org.apache.mesos.Protos;

/**
 * A {@link PortRangeRequirement} encapsulates a needed {@link MesosResource} representing a port range.
 */
public class PortRangeRequirement extends ResourceRequirement {
    private final String portName;
    private final int begin;
    private final int end;
    private final Optional<String> customEnvKey;

    public PortRangeRequirement(
            Protos.Resource resource, String portName, int begin, int end, Optional<String> customEnvKey) {
        super(resource);
        this.portName = portName;
        this.begin = begin;
        this.end = end;
        this.customEnvKey = customEnvKey;
    }

    public String getPortName() {
        return portName;
    }

    public int getBegin() {
        return begin;
    }

    public int getEnd() {
        return end;
    }

    public Optional<String> getCustomEnvKey() {
        return customEnvKey;
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new PortEvaluationStage(getResource(), taskName, getPortName(), getBegin(), getEnd(), getCustomEnvKey());
    }
}
