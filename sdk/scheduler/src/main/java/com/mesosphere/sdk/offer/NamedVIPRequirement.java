package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.NamedVIPEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;

import java.util.Optional;

import org.apache.mesos.Protos;

/**
 * A {@link NamedVIPRequirement} encapsulates a needed {@link MesosResource} representing a port with a DC/OS named VIP
 * attached.
 */
public class NamedVIPRequirement extends PortRequirement {
    private final String vipName;
    private final String protocol;
    private final Protos.DiscoveryInfo.Visibility visibility;
    private final long vipPort;

    protected NamedVIPRequirement(Builder builder) {
        super(builder);
        this.vipName = builder.vipName;
        this.vipPort = builder.vipPort;
        this.protocol = builder.protocol;
        this.visibility = builder.visibility;
    }

    public static Builder newBuilder(
            String role,
            String portName,
            String vipName,
            long port,
            long vipPort,
            String protocol) {
        return new Builder(role, portName, vipName, port, vipPort, protocol);
    }

    public String getProtocol() {
        return protocol;
    }

    public Protos.DiscoveryInfo.Visibility getVisibility() {
        return visibility;
    }

    public String getVipName() {
        return vipName;
    }

    public long getVipPort() {
        return vipPort;
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return null;
    }

    public static class Builder extends PortRequirement.Builder {
        private String vipName;
        private final long vipPort;
        private final String protocol;
        private Protos.DiscoveryInfo.Visibility visibility;

        protected Builder(String role, String portName, String vipName, long port, long vipPort, String protocol) {
            super(role, port, portName);
            this.vipName = vipName;
            this.vipPort = vipPort;
            this.protocol = protocol;
            this.visibility = Protos.DiscoveryInfo.Visibility.EXTERNAL;
        }

        public Builder visibility(Protos.DiscoveryInfo.Visibility visibility) {
            this.visibility = visibility;
            return this;
        }

        public NamedVIPRequirement build() {
            return new NamedVIPRequirement(this);
        }
    }
}
