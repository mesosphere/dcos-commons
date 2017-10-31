package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.specification.PodInstance;
import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * This rules implements a placement rule for restricting the maximum number of tasks per Zone.
 */
public class MaxPerZoneRule implements MaxPerRule {

    private final int maxPerZone;
    private final StringMatcher taskFilter;

    public MaxPerZoneRule(int maxPerZone) {
        this(maxPerZone, null);
    }

    @JsonCreator
    public MaxPerZoneRule(
            @JsonProperty("max") int maxPerZone,
            @JsonProperty("task-filter") StringMatcher taskFilter) {
        this.maxPerZone = maxPerZone;
        if (taskFilter == null) { // null when unspecified in serialized data
            taskFilter = AnyMatcher.create();
        }
        this.taskFilter = taskFilter;
    }

    @Override
    public Collection<String> getKeys(Protos.TaskInfo taskInfo) {
        Optional<String> zone = new TaskLabelReader(taskInfo).getZone();
        if (zone.isPresent()) {
            return Arrays.asList(zone.get());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public Collection<String> getKeys(Protos.Offer offer) {
        if (offer.hasDomain() && offer.getDomain().hasFaultDomain()) {
            return Arrays.asList(offer.getDomain().getFaultDomain().getZone().getName());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public StringMatcher getTaskFilter() {
        return taskFilter;
    }

    @Override
    public EvaluationOutcome filter(Protos.Offer offer, PodInstance podInstance, Collection<Protos.TaskInfo> tasks) {
        if (isAcceptable(offer, podInstance, tasks, maxPerZone)) {
            return EvaluationOutcome.pass(
                    this,
                    "Fewer than %d tasks matching filter '%s' are present on this host",
                    maxPerZone, taskFilter.toString())
                    .build();
        } else {
            return EvaluationOutcome.fail(
                    this,
                    "%d tasks matching filter '%s' are already present on this host",
                    maxPerZone, taskFilter.toString())
                    .build();
        }
    }
}
