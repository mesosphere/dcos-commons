package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.specification.PodInstance;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This interface defines the required methods for generic application of a PlacementRule which forces a
 * maximum per some key (e.g. attribute, hostname, region, zone ...).
 */
public abstract class MaxPerRule implements PlacementRule {
    @Valid
    @Min(1)
    protected final Integer max;
    private final StringMatcher taskFilter;

    public abstract Collection<String> getKeys(Protos.TaskInfo taskInfo);
    public abstract Collection<String> getKeys(Protos.Offer offer);

    @JsonProperty("task-filter")
    public StringMatcher getTaskFilter() {
        return taskFilter;
    }

    @JsonProperty("max")
    private int getMax() {
        return max;
    }

    /**
     * This rule rejects offers which exceed the maximum number of tasks on a given set of keys.
     * @param max The maximum number of tasks allowed on any given key.
     * @param taskFilter A filter which determines which tasks this rule applies.
     */
    protected MaxPerRule(Integer max, StringMatcher taskFilter) {
        this.max = max;
        this.taskFilter = taskFilter;
    }

    protected boolean isAcceptable(
            Protos.Offer offer,
            PodInstance podInstance,
            Collection<Protos.TaskInfo> tasks) {

        tasks = tasks.stream()
                .filter(task -> getTaskFilter().matches(task.getName()))
                .filter(task -> !PlacementUtils.areEquivalent(task, podInstance))
                .collect(Collectors.toList());

        Map<String, Integer> counts = new HashMap<>();

        Collection<String> offerKeys = getKeys(offer);
        updateMap(counts, offerKeys);

        for (Protos.TaskInfo task : tasks) {
            updateMap(
                    counts,
                    getKeys(task).stream()
                            .filter(key -> offerKeys.contains(key))
                            .collect(Collectors.toList()));
        }

        return counts.values().stream().allMatch(value -> value <= max);
    }

    private void updateMap(Map<String, Integer> map, Collection<String> keys) {
        for (String key : keys) {
            Integer count = map.get(key);
            count = count == null ? 1 : count + 1;
            map.put(key, count);
        }
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
