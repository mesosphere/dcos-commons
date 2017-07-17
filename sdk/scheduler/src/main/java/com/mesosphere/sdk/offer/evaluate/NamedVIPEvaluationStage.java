package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.api.EndpointUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mesosphere.sdk.specification.NamedVIPSpec;
import org.apache.mesos.Protos;

/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement}
 * for port resources as in {@link PortEvaluationStage}, additionally setting
 * {@link org.apache.mesos.Protos.DiscoveryInfo} properly for DC/OS to pick up the specified named VIP mapping.
 */
public class NamedVIPEvaluationStage extends PortEvaluationStage {

    private final NamedVIPSpec namedVIPSpec;

    public NamedVIPEvaluationStage(NamedVIPSpec namedVIPSpec, String taskName, Optional<String> resourceId) {
        super(namedVIPSpec, taskName, resourceId);
        this.namedVIPSpec = namedVIPSpec;
    }

    @Override
    protected void setProtos(PodInfoBuilder podInfoBuilder, Protos.Resource resource) {
        super.setProtos(podInfoBuilder, resource);

        // Find the port entry which was created above, and append VIP metadata to it.
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(getTaskName().get());
        List<Protos.Port.Builder> portBuilders =
                taskBuilder.getDiscoveryBuilder().getPortsBuilder().getPortsBuilderList().stream()
                        .filter(port -> port.getName().equals(portSpec.getPortName()))
                        .collect(Collectors.toList());
        if (portBuilders.size() != 1) {
            throw new IllegalStateException(String.format(
                    "Expected one port entry with name %s: %s", portSpec.getPortName(), portBuilders.toString()));
        }
        portBuilders.get(0)
                .setProtocol(namedVIPSpec.getProtocol())
                .getLabelsBuilder().addAllLabels(EndpointUtils.createVipLabels(
                        namedVIPSpec.getVipName(),
                        namedVIPSpec.getVipPort(),
                        !namedVIPSpec.getNetworkNames().isEmpty()));
    }
}
