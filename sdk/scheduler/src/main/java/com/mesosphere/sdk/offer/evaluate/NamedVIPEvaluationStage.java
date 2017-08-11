package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;

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

    public NamedVIPEvaluationStage(
            NamedVIPSpec namedVIPSpec,
            String taskName,
            Optional<String> resourceId) {
        super(namedVIPSpec, taskName, resourceId);
        this.namedVIPSpec = namedVIPSpec;
    }

    @Override
    protected void setProtos(PodInfoBuilder podInfoBuilder, Protos.Resource resource) {
        super.setProtos(podInfoBuilder, resource);

        // Find the matching port entry which was created above.
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(getTaskName().get());
        List<Protos.Port.Builder> portBuilders =
                taskBuilder.getDiscoveryBuilder().getPortsBuilder().getPortsBuilderList().stream()
                        .filter(port -> port.getName().equals(namedVIPSpec.getPortName()))
                        .collect(Collectors.toList());
        if (portBuilders.size() != 1) {
            throw new IllegalStateException(String.format(
                    "Expected one port entry with name %s: %s", namedVIPSpec.getPortName(), portBuilders.toString()));
        }

        // Update port entry with VIP metadata.
        Protos.Port.Builder portBuilder = portBuilders.get(0);
        portBuilder.setProtocol(namedVIPSpec.getProtocol());
        AuxLabelAccess.setVIPLabels(portBuilder, namedVIPSpec);
    }
}
