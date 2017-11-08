package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * @see RoundRobinByAttributeRule for a description of round robin behavior
 */
public class RoundRobinByZoneRule extends AbstractRoundRobinRule {

    protected RoundRobinByZoneRule(
            @JsonProperty("zone-count") Optional<Integer> zoneCount,
            @JsonProperty("task-filter") StringMatcher taskFilter) {
        super(taskFilter, zoneCount);
    }

    @Override
    protected String getKey(Protos.Offer offer) {
        if (offer.hasDomain() && offer.getDomain().hasFaultDomain()) {
            return offer.getDomain().getFaultDomain().getZone().getName();
        }

        return null;
    }

    @Override
    protected String getKey(Protos.TaskInfo task) {
        Optional<String> zone = new TaskLabelReader(task).getZone();
        return zone.isPresent() ? zone.get() : null;
    }

    @JsonProperty("zone-count")
    private Optional<Integer> getZoneCount() {
        return distinctKeyCount;
    }

    @JsonProperty("task-filter")
    private StringMatcher getTaskFilter() {
        return taskFilter;
    }

    @Override
    public String toString() {
        return String.format("RoundRobinByZoneRule{zone-count=%s, task-filter=%s}",
                distinctKeyCount, taskFilter);
    }
}
