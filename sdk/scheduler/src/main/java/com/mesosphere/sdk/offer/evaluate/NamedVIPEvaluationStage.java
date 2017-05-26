package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.OfferRequirement;

import java.util.*;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Resource;


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
    private final boolean onOverlay;

    public NamedVIPEvaluationStage(
            Protos.Resource resource,
            String taskName,
            String portName,
            int port,
            Optional<String> customEnvKey,
            String protocol,
            DiscoveryInfo.Visibility visibility,
            String vipName,
            Integer vipPort,
            boolean useHostPorts,
            boolean onOverlay) {
        super(resource, taskName, portName, port, customEnvKey, useHostPorts);
        this.protocol = protocol;
        this.visibility = visibility;
        this.vipName = vipName;
        this.vipPort = vipPort;
        this.onOverlay = onOverlay;
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
                if (l.getKey().startsWith(Constants.VIP_PREFIX) &&
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

    private DiscoveryInfo.Builder addVIP(
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
                .addAllLabels(getVIPLabels(vipName, vipPort, onOverlay));

        // Ensure Discovery visibility is always CLUSTER. This is to update visibility if prior info
        // (i.e. upgrading an old service with a previous version of SDK) has different visibility.
        builder.setVisibility(DiscoveryInfo.Visibility.CLUSTER);
        return builder;
    }

    private DiscoveryInfo getVIPDiscoveryInfo(
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
                .addAllLabels(getVIPLabels(vipName, vipPort, onOverlay));

        return discoveryInfoBuilder.build();
    }

    private static Collection<Label> getVIPLabels(String vipName, Integer vipPort, boolean onOverlay) {
        List<Label> labels = new ArrayList<>();
        labels.add(Label.newBuilder()
                .setKey(String.format("%s%s", Constants.VIP_PREFIX, UUID.randomUUID().toString()))
                .setValue(String.format("%s:%d", vipName, vipPort))
                .build());
        if (onOverlay) {
            labels.add(Label.newBuilder()
                    .setKey(String.format("%s", Constants.VIP_OVERLAY_FLAG_KEY))
                    .setValue(String.format("%s", Constants.VIP_OVERLAY_FLAG_VALUE))
                    .build());
        }
        return labels;
    }
}
