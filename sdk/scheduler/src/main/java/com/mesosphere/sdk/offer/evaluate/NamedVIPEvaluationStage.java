package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.api.EndpointUtils;
import com.mesosphere.sdk.offer.OfferRequirement;

import java.util.Optional;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;
import org.apache.mesos.Protos.Resource;


/**
 * This class evaluates an offer against a given {@link OfferRequirement} for port resources as in
 * {@link PortEvaluationStage}, additionally setting {@link org.apache.mesos.Protos.DiscoveryInfo} properly for
 * DC/OS to pick up the specified named VIP mapping.
 */
public class NamedVIPEvaluationStage extends PortEvaluationStage {

    private final String taskName;
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
        this.taskName = taskName;
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
        } else if (podInfoBuilder.getExecutorBuilder().isPresent()) {
            boolean didUpdate = maybeUpdateVIP(podInfoBuilder.getExecutorBuilder().get());

            if (!didUpdate) {
                // Set the VIP on the ExecutorInfo.
                Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
                if (executorBuilder.hasDiscovery()) {
                    addVIP(
                            executorBuilder.getDiscoveryBuilder(),
                            vipName,
                            protocol,
                            visibility,
                            vipPort,
                            (int) resource.getRanges().getRange(0).getBegin());
                } else {
                    executorBuilder.setDiscovery(getVIPDiscoveryInfo(
                            executorBuilder.getName(),
                            vipName,
                            vipPort,
                            protocol,
                            visibility,
                            resource));
                }
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
                Optional<EndpointUtils.VipInfo> vipInfo = EndpointUtils.parseVipLabel(taskName, l);
                if (vipInfo.isPresent()
                        && vipInfo.get().getVipName().equals(vipName)
                        && vipInfo.get().getVipPort() == vipPort) {
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

    private static DiscoveryInfo.Builder addVIP(
            DiscoveryInfo.Builder builder,
            String vipName,
            String protocol,
            DiscoveryInfo.Visibility visibility,
            Integer vipPort,
            int destPort) {
        builder.getPortsBuilder()
                .addPortsBuilder()
                .setNumber(destPort)
                .setProtocol(protocol)
                .setVisibility(visibility)
                .getLabelsBuilder()
                .addLabels(EndpointUtils.createVipLabel(vipName, vipPort));

        // Ensure Discovery visibility is always CLUSTER. This is to update visibility if prior info
        // (i.e. upgrading an old service with a previous version of SDK) has different visibility.
        builder.setVisibility(DiscoveryInfo.Visibility.CLUSTER);
        return builder;
    }

    private static DiscoveryInfo getVIPDiscoveryInfo(
            String taskName,
            String vipName,
            Integer vipPort,
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
                .addLabels(EndpointUtils.createVipLabel(vipName, vipPort));

        return discoveryInfoBuilder.build();
    }
}
