package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.NamedVIPEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;

import java.util.Collection;
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
    private boolean onOverlay;

    public NamedVIPRequirement(
            Protos.Resource resource,
            String portName,
            int port,
            Optional<String> customEnvKey,
            String protocol,
            Protos.DiscoveryInfo.Visibility visibility,
            String vipName,
            int vipPort,
            Collection<String> networkNames) {
        super(resource, portName, port, customEnvKey, networkNames);
        this.protocol = protocol;
        this.visibility = visibility;
        this.vipName = vipName;
        this.vipPort = vipPort;
        this.onOverlay = useContainerNetwork(networkNames);
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

    public boolean isOnOverlay() {
        return onOverlay;
    }

    private boolean useContainerNetwork(Collection<String> networkNames) {
        return !networkNames.isEmpty();
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
                getVipPort(),
                super.usingHostPorts(),
                isOnOverlay());
    }
}
