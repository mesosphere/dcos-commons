package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.api.EndpointUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mesosphere.sdk.specification.NamedVIPSpec;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;


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

    public NamedVIPEvaluationStage(
            NamedVIPSpec namedVIPSpec,
            String taskName,
            Optional<String> resourceId,
            String portName,
            boolean useDefaultExecutor) {
        super(namedVIPSpec, taskName, resourceId, portName, useDefaultExecutor);
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
                taskBuilder.getDiscoveryBuilder().setVisibility(DiscoveryInfo.Visibility.CLUSTER);
                List<Protos.Port.Builder> portsBuilders = taskBuilder
                        .getDiscoveryBuilder()
                        .getPortsBuilder()
                            .getPortsBuilderList().stream()
                            .filter(port -> port.getNumber() == portSpec.getPort())
                            .collect(Collectors.toList());
                if (portsBuilders.size() != 1) {
                    throw new IllegalStateException(String.format("Cannot have multiple ports with the same number" +
                            "got ports %s", portsBuilders.toString()));
                }
                Protos.Port.Builder portBuilder = portsBuilders.get(0);
                if (!portBuilder.getName().equals(getPortName())) {
                    throw new IllegalStateException(String.format("Port has incorrect name/port pair got %s" +
                            " should have name %s", portBuilder.getName(), portSpec.getPortName()));
                }
                portBuilder.setVisibility(visibility)
                        .setProtocol(protocol)
                        .getLabelsBuilder()
                            .addAllLabels(EndpointUtils.createVipLabels(vipName, vipPort, onNamedNetwork));
            } else {
                throw new IllegalStateException(String.format("TaskBuilder missing DiscoveryInfo for port" +
                        "%s, TaskBuilder: %s", getPortName(), taskBuilder.toString()));
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
                            .setName(getPortName())
                            .setProtocol(protocol);
                    return true;
                }
            }
        }

        return false;
    }
}
