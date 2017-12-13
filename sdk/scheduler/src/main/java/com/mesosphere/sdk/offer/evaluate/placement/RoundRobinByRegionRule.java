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
public class RoundRobinByRegionRule extends AbstractRoundRobinRule {

    public RoundRobinByRegionRule(Integer regionCount) {
        this(Optional.of(regionCount));
    }

    public RoundRobinByRegionRule(Optional<Integer> regionCount) {
        this(regionCount, null);
    }

    public RoundRobinByRegionRule(
            @JsonProperty("region-count") Optional<Integer> regionCount,
            @JsonProperty("task-filter") StringMatcher taskFilter) {
        super(taskFilter, regionCount);
    }

    @Override
    protected String getKey(Protos.Offer offer) {
        if (offer.hasDomain() && offer.getDomain().hasFaultDomain()) {
            return offer.getDomain().getFaultDomain().getRegion().getName();
        }

        return null;
    }

    @Override
    protected String getKey(Protos.TaskInfo task) {
        Optional<String> region = new TaskLabelReader(task).getRegion();
        return region.isPresent() ? region.get() : null;
    }

    @JsonProperty("region-count")
    private Optional<Integer> getRegionCount() {
        return distinctKeyCount;
    }

    @Override
    public String toString() {
        return String.format("RoundRobinByRegionRule{region-count=%s, task-filter=%s}",
                distinctKeyCount, taskFilter);
    }

    @Override
    public Collection<PlacementField> getPlacementFields() {
        return Arrays.asList(PlacementField.REGION);
    }
}
