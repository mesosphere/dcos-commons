package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.taskdata.AttributeStringUtils;
import com.mesosphere.sdk.specification.PodInstance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Requires that the Offer contain an attribute whose string representation matches the provided string.
 *
 * @see AttributeStringUtils#toString(Attribute)
 */
public class AttributeRule extends StringMatcherRule {

  @JsonCreator
  public AttributeRule(@JsonProperty("matcher") StringMatcher matcher) {
    super("AttributeRule", matcher);
  }

  @Override
  public EvaluationOutcome filter(
      Offer offer,
      PodInstance podInstance,
      Collection<TaskInfo> tasks)
  {
    if (isAcceptable(offer, podInstance, tasks)) {
      return EvaluationOutcome.pass(
          this,
          "Match found for attribute pattern: '%s'", getMatcher().toString())
          .build();
    } else {
      return EvaluationOutcome.fail(
          this,
          "None of %d attributes matched pattern: '%s'",
          offer.getAttributesCount(),
          getMatcher().toString())
          .build();
    }
  }

  @Override
  public Collection<PlacementField> getPlacementFields() {
    return Collections.singletonList(PlacementField.ATTRIBUTE);
  }

  @Override
  public Collection<String> getKeys(Offer offer) {
    return offer.getAttributesList().stream()
        .map(AttributeStringUtils::toString)
        .collect(Collectors.toList());
  }
}
