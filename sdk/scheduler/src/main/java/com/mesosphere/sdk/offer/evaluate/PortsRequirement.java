package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.ResourceRequirement;
import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link PortsRequirement} encapsulates a needed {@link com.mesosphere.sdk.offer.MesosResource} representing multiple
 * ports.
 */
public class PortsRequirement extends ResourceRequirement {
    private final Collection<ResourceRequirement> portRequirements;

    public PortsRequirement(Collection<ResourceRequirement> portRequirements) {
        super(coalescePorts(portRequirements.stream().map(r -> r.getResource()).collect(Collectors.toList())));
        this.portRequirements = portRequirements;
    }

    public Collection<ResourceRequirement> getPortRequirements() {
        return portRequirements;
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new PortsEvaluationStage(
                portRequirements.stream().map(r -> r.getEvaluationStage(taskName)).collect(Collectors.toList()));
    }

    private static Protos.Resource coalescePorts(List<Protos.Resource> resources) {
        if (CollectionUtils.isEmpty(resources)) {
            throw new IllegalArgumentException("list of port resources to coalesce must not be empty");
        }

        Protos.Resource.Builder builder = resources.get(0).toBuilder();
        Set<String> existingLabels = new HashSet<>();
        for (Protos.Resource r : resources) {
            builder.getRangesBuilder().addRange(r.getRanges().getRange(0));
            for (Protos.Label l : r.getReservation().getLabels().getLabelsList()) {
                if (existingLabels.contains(l.getKey())) {
                    continue;
                }

                builder.getReservationBuilder().getLabelsBuilder().addLabels(l);
            }
        }

        return builder.build();
    }
}
