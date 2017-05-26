package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.ResourceUtils;

import java.util.Optional;

import com.mesosphere.sdk.specification.NamedVIPSpec;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;


/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement}
 * for port resources as in {@link PortEvaluationStage}, additionally setting
 * {@link org.apache.mesos.Protos.DiscoveryInfo} properly for DC/OS to pick up the specified named VIP mapping.
 */
public class NamedVIPEvaluationStage extends PortEvaluationStage {
    private final String protocol;
    private final DiscoveryInfo.Visibility visibility;
    private final String vipName;
    private final long vipPort;

    public NamedVIPEvaluationStage(NamedVIPSpec namedVIPSpec, String taskName, Optional<String> resourceId) {
        super(namedVIPSpec, taskName, resourceId);
        this.protocol = namedVIPSpec.getProtocol();
        this.visibility = namedVIPSpec.getVisibility();
        this.vipName = namedVIPSpec.getVipName();
        this.vipPort = namedVIPSpec.getVipPort();
    }

    @Override
    protected void setProtos(PodInfoBuilder podInfoBuilder, Protos.Resource resource) {
        super.setProtos(podInfoBuilder, resource);

        // If the VIP is already set, we don't have to do anything.
        boolean didUpdate = maybeUpdateVIP(podInfoBuilder.getTaskBuilder(getTaskName().get()));
        if (!didUpdate) {
            // Add a new VIP entry to the TaskInfo.
            Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(getTaskName().get());
            ResourceUtils.addVIP(taskBuilder, vipName, vipPort, protocol, visibility, resource);
        }
    }

    private boolean maybeUpdateVIP(Protos.TaskInfo.Builder builder) {
        if (!builder.hasDiscovery()) {
            return false;
        }

        for (Protos.Port.Builder portBuilder : builder.getDiscoveryBuilder().getPortsBuilder().getPortsBuilderList()) {
            for (Protos.Label l : portBuilder.getLabels().getLabelsList()) {
                if (l.getKey().startsWith(ResourceUtils.VIP_PREFIX) &&
                        l.getValue().equals(String.format("%s:%d", vipName, vipPort))) {
                    portBuilder
                        .setNumber((int) getPort())
                        .setVisibility(visibility)
                        .setProtocol(protocol);
                    return true;
                }
            }
        }
        return false;
    }
}
