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
public class RoundRobinByRegionRule extends AbstractRoundRobinRule {

    protected RoundRobinByRegionRule(
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
        return distinctValueCount;
    }

    @JsonProperty("task-filter")
    private StringMatcher getTaskFilter() {
        return taskFilter;
    }

    @Override
    public String toString() {
        return String.format("RoundRobinByRegionRule{region-count=%s, task-filter=%s}",
                distinctValueCount, taskFilter);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
