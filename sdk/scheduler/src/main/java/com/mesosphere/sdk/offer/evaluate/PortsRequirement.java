package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.PortRequirement;
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
public class PortsRequirement {
    private final Collection<PortRequirement> portRequirements;

    public PortsRequirement(Collection<PortRequirement> portRequirements) {
        this.portRequirements = portRequirements;
    }

    public Collection<PortRequirement> getPortRequirements() {
        return portRequirements;
    }

    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new MultiEvaluationStage(
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
