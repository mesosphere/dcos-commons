package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * @see RoundRobinByAttributeRule for a description of round robin behavior
 */
public class RoundRobinByZoneRule extends AbstractRoundRobinRule {
    public RoundRobinByZoneRule(Integer zoneCount) {
        this(Optional.of(zoneCount));
    }

    public RoundRobinByZoneRule(Optional<Integer> zoneCount) {
        this(zoneCount, null);
    }

    public RoundRobinByZoneRule(
            @JsonProperty("zone-count") Optional<Integer> zoneCount,
            @JsonProperty("task-filter") StringMatcher taskFilter) {
        super(taskFilter, zoneCount);
    }

    @Override
    public String getKey(Protos.Offer offer) {
        if (offer.hasDomain() && offer.getDomain().hasFaultDomain()) {
            return offer.getDomain().getFaultDomain().getZone().getName();
        }

        return null;
    }

    @Override
    public String getKey(Protos.TaskInfo task) {
        Optional<String> zone = new TaskLabelReader(task).getZone();
        return zone.isPresent() ? zone.get() : null;
    }

    @JsonProperty("zone-count")
    private Optional<Integer> getZoneCount() {
        return distinctKeyCount;
    }

    @Override
    public String toString() {
        return String.format("RoundRobinByZoneRule{zone-count=%s, task-filter=%s}",
                distinctKeyCount, taskFilter);
    }

    @Override
    public Collection<PlacementField> getPlacementFields() {
        return Arrays.asList(PlacementField.ZONE);
    }
}
