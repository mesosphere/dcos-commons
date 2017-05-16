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
    private final Optional<String> envKey;

    public PortRequirement(Builder builder) {
        super(builder);
        this.portName = builder.portName;
        this.port = builder.port;
        this.envKey = Optional.ofNullable(builder.envKey);
    }

    public String getPortName() {
        return portName;
    }

    public int getPort() {
        return port;
    }

    public Optional<String> getEnvKey() {
        return envKey;
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new PortEvaluationStage(this, taskName, getPortName(), getPort(), getEnvKey());
    }

    public static class Builder extends ResourceRequirement.Builder {
        private final int port;
        private String portName;
        private String envKey;

        public Builder(String role, int port, String portName) {
            super(
                    role,
                    Constants.PORTS_RESOURCE_TYPE,
                    Protos.Value.newBuilder()
                            .setType(Protos.Value.Type.RANGES)
                            .setRanges(Protos.Value.Ranges.newBuilder()
                                    .addRange(Protos.Value.Range.newBuilder()
                                            .setBegin(port)
                                            .setEnd(port)))
                            .build());
            this.port = port;
            this.portName = portName;
        }

        public Builder portName(String portName) {
            this.portName = portName;
            return this;
        }

        public Builder envKey(String envKey) {
            this.envKey = envKey;
            return this;
        }

        public PortRequirement build() {
            return new PortRequirement(this);
        }
    }
}
