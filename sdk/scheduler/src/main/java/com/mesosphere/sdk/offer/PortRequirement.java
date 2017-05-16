package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.PortEvaluationStage;
import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * A {@link PortRequirement} encapsulates a needed {@link MesosResource} representing a port.
 */
public class PortRequirement extends ResourceRequirement {
    private final String portName;
    private final long port;
    private final Optional<String> envKey;

    protected PortRequirement(Builder builder) {
        super(builder);
        this.portName = builder.portName;
        this.port = builder.port;
        this.envKey = Optional.ofNullable(builder.envKey);
    }

    public static Builder newBuilder(String role, int port, String portname) {
        return new Builder(role, port, portname);
    }

    public static Builder newBuilder(Protos.Resource resource, String portName) {
        return new Builder(resource, portName);
    }

    public String getPortName() {
        return portName;
    }

    public long getPort() {
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
        private final long port;
        private String portName;
        private String envKey;

        protected Builder(String role, long port, String portName) {
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

        protected Builder(Protos.Resource resource, String portName) {
            this(resource.getRole(), getPort(resource), portName);
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

        protected static long getPort(Protos.Resource resource) {
            if (!resource.getType().equals(Protos.Value.Type.RANGES)) {
                throw new IllegalStateException("A port resource must be of type RANGES");
            }

            if (resource.getRanges().getRangeCount() != 1)  {
                throw new IllegalStateException("A port resource must contain a single range.");
            }

            long begin = resource.getRanges().getRange(0).getBegin();
            long end = resource.getRanges().getRange(0).getEnd();
            if (begin != end) {
                throw new IllegalStateException("A port resource must contain a single port.");
            }

            return begin;
        }
    }
}
