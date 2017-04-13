package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.ResourceUtils;

import java.util.Optional;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;


/**
 * This class evaluates an offer against a given {@link OfferRequirement} for port resources as in
 * {@link PortEvaluationStage}, additionally setting {@link org.apache.mesos.Protos.DiscoveryInfo} properly for
 * DC/OS to pick up the specified named VIP mapping.
 */
public class NamedVIPEvaluationStage extends PortEvaluationStage {
    private final String protocol;
    private final DiscoveryInfo.Visibility visibility;
    private final String vipName;
    private final Integer vipPort;

    public NamedVIPEvaluationStage(
            Protos.Resource resource,
            String taskName,
            String portName,
            int port,
            Optional<String> customEnvKey,
            String protocol,
            DiscoveryInfo.Visibility visibility,
            String vipName,
            Integer vipPort) {
        super(resource, taskName, portName, port, customEnvKey);
        this.protocol = protocol;
        this.visibility = visibility;
        this.vipName = vipName;
        this.vipPort = vipPort;
    }

    @Override
    protected void setProtos(PodInfoBuilder podInfoBuilder, Protos.Resource resource) {
        super.setProtos(podInfoBuilder, resource);

        // If this is an existing TaskInfo or ExecutorInfo with the VIP already set, we don't have to do anything.
        if (getTaskName().isPresent()) {
            boolean didUpdate = maybeUpdateVIP(podInfoBuilder.getTaskBuilder(getTaskName().get()));

            if (!didUpdate) {
                // Set the VIP on the TaskInfo.
                Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(getTaskName().get());
                ResourceUtils.addVIP(taskBuilder, vipName, vipPort, protocol, visibility, resource);
            }
        } else if (podInfoBuilder.getExecutorBuilder().isPresent()) {
            boolean didUpdate = maybeUpdateVIP(podInfoBuilder.getExecutorBuilder().get());

            if (!didUpdate) {
                // Set the VIP on the ExecutorInfo.
                Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
                ResourceUtils.addVIP(executorBuilder, vipName, vipPort, protocol, visibility, resource);
            }
        }
    }

    private boolean maybeUpdateVIP(Protos.TaskInfo.Builder builder) {
        if (!builder.hasDiscovery()) {
            return false;
        }

        return maybeUpdateVIP(builder.getDiscoveryBuilder());
    }

    private boolean maybeUpdateVIP(Protos.ExecutorInfo.Builder builder) {
        if (!builder.hasDiscovery()) {
            return false;
        }

        return maybeUpdateVIP(builder.getDiscoveryBuilder());
    }

    private boolean maybeUpdateVIP(Protos.DiscoveryInfo.Builder builder) {
        for (Protos.Port.Builder portBuilder : builder.getPortsBuilder().getPortsBuilderList()) {
            for (Protos.Label l : portBuilder.getLabels().getLabelsList()) {
                if (l.getKey().startsWith(ResourceUtils.VIP_PREFIX) &&
                        l.getValue().equals(String.format("%s:%d", vipName, vipPort))) {
                    portBuilder.setNumber(
                            (int) getResourceRequirement().getResource().getRanges().getRange(0).getBegin());
                    portBuilder.setVisibility(visibility);
                    portBuilder.setProtocol(protocol);
                    return true;
                }
            }
        }

        return false;
    }
}
