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
    private final String protocol;
    private final Protos.DiscoveryInfo.Visibility visibility;
    private final String vipName;
    private final int vipPort;

    public NamedVIPRequirement(
            Protos.Resource resource,
            String portName,
            int port,
            Optional<String> customEnvKey,
            String protocol,
            Protos.DiscoveryInfo.Visibility visibility,
            String vipName,
            int vipPort) {
        super(resource, portName, port, customEnvKey);
        this.protocol = protocol;
        this.visibility = visibility;
        this.vipName = vipName;
        this.vipPort = vipPort;
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

    public int getVipPort() {
        return vipPort;
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new NamedVIPEvaluationStage(
                getResource(),
                taskName,
                getPortName(),
                getPort(),
                getCustomEnvKey(),
                getProtocol(),
                getVisibility(),
                getVipName(),
                getVipPort());
    }
}
