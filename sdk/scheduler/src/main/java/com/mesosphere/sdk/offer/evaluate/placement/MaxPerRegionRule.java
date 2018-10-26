package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.specification.PodInstance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.mesos.Protos;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * This rules implements a placement rule for restricting the maximum number of tasks per Region.
 */
public class MaxPerRegionRule extends MaxPerRule {
  public MaxPerRegionRule(int maxPerRegion) {
    this(maxPerRegion, null);
  }

  @JsonCreator
  public MaxPerRegionRule(
      @JsonProperty("max") Integer max,
      @JsonProperty("task-filter") StringMatcher taskFilter)
  {
    super(max, taskFilter);
  }

  @Override
  public Collection<String> getKeys(Protos.TaskInfo taskInfo) {
    Optional<String> region = new TaskLabelReader(taskInfo).getRegion();
    return region.map(Arrays::asList).orElse(Collections.emptyList());
  }

  @Override
  public Collection<String> getKeys(Protos.Offer offer) {
    if (offer.hasDomain() && offer.getDomain().hasFaultDomain()) {
      return Collections.singletonList(offer.getDomain().getFaultDomain().getRegion().getName());
    }

    return Collections.emptyList();
  }

  @Override
  public EvaluationOutcome filter(
      Protos.Offer offer,
      PodInstance podInstance,
      Collection<Protos.TaskInfo> tasks)
  {
    if (isAcceptable(offer, podInstance, tasks)) {
      return EvaluationOutcome.pass(
          this,
          "Fewer than %d tasks matching filter '%s' are present on this host",
          max, getTaskFilter().toString())
          .build();
    } else {
      return EvaluationOutcome.fail(
          this,
          "%d tasks matching filter '%s' are already present on this host",
          max, getTaskFilter().toString())
          .build();
    }
  }

  @Override
  public Collection<PlacementField> getPlacementFields() {
    return Collections.singletonList(PlacementField.REGION);
  }
}
