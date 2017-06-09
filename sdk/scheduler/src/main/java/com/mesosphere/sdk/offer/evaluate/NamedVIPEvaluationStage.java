package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.api.EndpointUtils;

import java.util.Optional;

import com.mesosphere.sdk.specification.NamedVIPSpec;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;
import org.apache.mesos.Protos.Resource;


/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement}
 * for port resources as in {@link PortEvaluationStage}, additionally setting
 * {@link org.apache.mesos.Protos.DiscoveryInfo} properly for DC/OS to pick up the specified named VIP mapping.
 */
public class NamedVIPEvaluationStage extends PortEvaluationStage {

    private final String taskName;
    private final String protocol;
    private final DiscoveryInfo.Visibility visibility;
    private final String vipName;
    private final long vipPort;
    private final boolean onNamedNetwork;

    public NamedVIPEvaluationStage(NamedVIPSpec namedVIPSpec, String taskName, Optional<String> resourceId) {
        super(namedVIPSpec, taskName, resourceId);
        this.taskName = taskName;
        this.protocol = namedVIPSpec.getProtocol();
        this.visibility = namedVIPSpec.getVisibility();
        this.vipName = namedVIPSpec.getVipName();
        this.vipPort = namedVIPSpec.getVipPort();
        this.onNamedNetwork = !namedVIPSpec.getNetworkNames().isEmpty();
    }

    @Override
    protected void setProtos(PodInfoBuilder podInfoBuilder, Protos.Resource resource) {
        super.setProtos(podInfoBuilder, resource);

        // If the VIP is already set, we don't have to do anything.
        boolean didUpdate = maybeUpdateVIP(podInfoBuilder.getTaskBuilder(getTaskName().get()));
        if (!didUpdate) {
            // Set the VIP on the TaskInfo.
            Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(getTaskName().get());
            if (taskBuilder.hasDiscovery()) {
                addVIP(
                        taskBuilder.getDiscoveryBuilder(),
                        vipName,
                        protocol,
                        visibility,
                        vipPort,
                        (int) resource.getRanges().getRange(0).getBegin());
            } else {
                taskBuilder.setDiscovery(getVIPDiscoveryInfo(
                        taskBuilder.getName(),
                        vipName,
                        vipPort,
                        protocol,
                        visibility,
                        resource));
            }
        }
    }

    private boolean maybeUpdateVIP(Protos.TaskInfo.Builder builder) {
        if (!builder.hasDiscovery()) {
            return false;
        }

        for (Protos.Port.Builder portBuilder : builder.getDiscoveryBuilder().getPortsBuilder().getPortsBuilderList()) {
            for (Protos.Label l : portBuilder.getLabels().getLabelsList()) {
                Optional<EndpointUtils.VipInfo> vipInfo = EndpointUtils.parseVipLabel(taskName, l);
                if (vipInfo.isPresent()
                        && vipInfo.get().getVipName().equals(vipName)
                        && vipInfo.get().getVipPort() == vipPort) {
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

    private DiscoveryInfo.Builder addVIP(
            DiscoveryInfo.Builder builder,
            String vipName,
            String protocol,
            DiscoveryInfo.Visibility visibility,
            long vipPort,
            int destPort) {
        builder.getPortsBuilder()
                .addPortsBuilder()
                .setNumber(destPort)
                .setProtocol(protocol)
                .setVisibility(visibility)
                .getLabelsBuilder()
                .addAllLabels(EndpointUtils.createVipLabels(vipName, vipPort, onNamedNetwork));

        // Ensure Discovery visibility is always CLUSTER. This is to update visibility if prior info
        // (i.e. upgrading an old service with a previous version of SDK) has different visibility.
        builder.setVisibility(DiscoveryInfo.Visibility.CLUSTER);
        return builder;
    }

    private DiscoveryInfo getVIPDiscoveryInfo(
            String taskName,
            String vipName,
            long vipPort,
            String protocol,
            DiscoveryInfo.Visibility visibility,
            Resource r) {
        DiscoveryInfo.Builder discoveryInfoBuilder = DiscoveryInfo.newBuilder()
                .setVisibility(DiscoveryInfo.Visibility.CLUSTER)
                .setName(taskName);

        discoveryInfoBuilder.getPortsBuilder().addPortsBuilder()
                .setNumber((int) r.getRanges().getRange(0).getBegin())
                .setProtocol(protocol)
                .setVisibility(visibility)
                .getLabelsBuilder()
                .addAllLabels(EndpointUtils.createVipLabels(vipName, vipPort, onNamedNetwork));

        return discoveryInfoBuilder.build();
    }
}
