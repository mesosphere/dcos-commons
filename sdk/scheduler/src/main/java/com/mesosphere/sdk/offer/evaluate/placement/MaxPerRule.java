package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.specification.PodInstance;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This interface defines the required methods for generic application of a PlacementRule which forces a
 * maximum per some key (e.g. attribute, hostname, region, zone ...).
 */
public abstract class MaxPerRule implements PlacementRule {
    public abstract Collection<String> getKeys(Protos.TaskInfo taskInfo);
    public abstract Collection<String> getKeys(Protos.Offer offer);
    public abstract StringMatcher getTaskFilter();

    public boolean isAcceptable(
            Protos.Offer offer,
            PodInstance podInstance,
            Collection<Protos.TaskInfo> tasks,
            Integer max) {

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
}
