package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResource;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ReserveOfferRecommendation;
import com.mesosphere.sdk.offer.ResourceBuilder;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.UnreserveOfferRecommendation;
import com.mesosphere.sdk.offer.ValueUtils;
import com.mesosphere.sdk.offer.taskdata.AttributeStringUtils;
import com.mesosphere.sdk.specification.DefaultResourceSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

/**
 * This class encapsulates shared offer evaluation logic for evaluation stages.
 */
final class OfferEvaluationUtils {

  private OfferEvaluationUtils() {}

  static ReserveEvaluationOutcome evaluateSimpleResource(
      Logger logger,
      OfferEvaluationStage offerEvaluationStage,
      ResourceSpec resourceSpec,
      Optional<String> resourceId,
      Optional<String> resourceNamespace,
      MesosResourcePool mesosResourcePool)
  {

    Optional<MesosResource> mesosResourceOptional;
    if (!resourceId.isPresent()) {
      mesosResourceOptional = mesosResourcePool.consumeReservableMerged(
          resourceSpec.getName(), resourceSpec.getValue(), resourceSpec.getPreReservedRole());
    } else {
      mesosResourceOptional = mesosResourcePool.consumeReserved(
          resourceSpec.getName(), resourceSpec.getValue(), resourceId.get());
    }
    if (!mesosResourceOptional.isPresent()) {
      return new ReserveEvaluationOutcome(
          EvaluationOutcome.fail(
              offerEvaluationStage,
              "Offer failed to satisfy: %s with resourceId: %s",
              resourceSpec,
              resourceId)
              .build(),
          null);
    }

    OfferRecommendation offerRecommendation = null;
    MesosResource mesosResource = mesosResourceOptional.get();

    if (ValueUtils.equal(mesosResource.getValue(), resourceSpec.getValue())) {
      logger.info("Resource '{}' matches required value {}: wanted {}, got {}",
          resourceSpec.getName(),
          resourceId.isPresent() ? "(previously reserved)" : "(requires RESERVE operation)",
          AttributeStringUtils.toString(resourceSpec.getValue()),
          AttributeStringUtils.toString(mesosResource.getValue()));

      if (!resourceId.isPresent()) {
        // Initial reservation of resources
        Protos.Resource resource = ResourceBuilder
            .fromSpec(resourceSpec, resourceId, resourceNamespace)
            .setMesosResource(mesosResource)
            .build();
        offerRecommendation =
            new ReserveOfferRecommendation(mesosResourcePool.getOffer(), resource);
        return new ReserveEvaluationOutcome(
            EvaluationOutcome.pass(
                offerEvaluationStage,
                Collections.singletonList(offerRecommendation),
                "Offer contains sufficient '%s': for resource: '%s' with resourceId: '%s'",
                resourceSpec.getName(),
                resourceSpec,
                resourceId)
                .mesosResource(mesosResource)
                .build(),
            ResourceUtils.getResourceId(resource).get());
      } else {
        return new ReserveEvaluationOutcome(
            EvaluationOutcome.pass(
                offerEvaluationStage,
                Collections.emptyList(),
                "Offer contains sufficient previously reserved '%s':" +
                    " for resource: '%s' with resourceId: '%s'",
                resourceSpec.getName(),
                resourceSpec,
                resourceId)
                .mesosResource(mesosResource)
                .build(),
            resourceId.get());
      }
    } else {
      Protos.Value difference =
          ValueUtils.subtract(resourceSpec.getValue(), mesosResource.getValue());
      if (ValueUtils.compare(difference, ValueUtils.getZero(difference.getType())) > 0) {
        logger.info(
            "Reservation for resource '{}' needs increasing from current '{}' to required '{}' " +
                "(add: '{}' from role: '{}')",
            resourceSpec.getName(),
            AttributeStringUtils.toString(mesosResource.getValue()),
            AttributeStringUtils.toString(resourceSpec.getValue()),
            AttributeStringUtils.toString(difference),
            resourceSpec.getPreReservedRole()
        );

        ResourceSpec requiredAdditionalResources = DefaultResourceSpec.newBuilder(resourceSpec)
            .value(difference)
            .build();
        mesosResourceOptional = mesosResourcePool.consumeReservableMerged(
            requiredAdditionalResources.getName(),
            requiredAdditionalResources.getValue(),
            resourceSpec.getPreReservedRole());

        if (!mesosResourceOptional.isPresent()) {
          return new ReserveEvaluationOutcome(
              EvaluationOutcome.fail(offerEvaluationStage,
                  "Insufficient resources to increase reservation of existing resource '%s' with " +
                      "resourceId '%s': needed %s",
                  resourceSpec,
                  resourceId,
                  AttributeStringUtils.toString(difference))
                  .build(),
              null);
        }

        mesosResource = mesosResourceOptional.get();
        Protos.Resource resource = ResourceBuilder
            .fromSpec(resourceSpec, resourceId, resourceNamespace)
            .setValue(mesosResource.getValue())
            .build();
        // Reservation of additional resources
        offerRecommendation =
            new ReserveOfferRecommendation(mesosResourcePool.getOffer(), resource);
        return new ReserveEvaluationOutcome(
            EvaluationOutcome.pass(
                offerEvaluationStage,
                Collections.singletonList(offerRecommendation),
                "Offer contains sufficient '%s': for " +
                    "increasing resource: '%s' with resourceId: '%s'",
                resourceSpec.getName(),
                resourceSpec,
                resourceId)
                .mesosResource(mesosResource)
                .build(),
            ResourceUtils.getResourceId(resource).get());
      } else {
        Protos.Value unreserve =
            ValueUtils.subtract(mesosResource.getValue(), resourceSpec.getValue());
        logger.info(
            "Reservation for resource '{}' needs decreasing from current {} to required {} " +
                "(subtract: {})",
            resourceSpec.getName(),
            AttributeStringUtils.toString(mesosResource.getValue()),
            AttributeStringUtils.toString(resourceSpec.getValue()),
            AttributeStringUtils.toString(Objects.requireNonNull(unreserve)));

        Protos.Resource resource = ResourceBuilder
            .fromSpec(resourceSpec, resourceId, resourceNamespace)
            .setValue(unreserve)
            .build();
        // Unreservation of no longer needed resources
        offerRecommendation =
            new UnreserveOfferRecommendation(mesosResourcePool.getOffer(), resource);
        return new ReserveEvaluationOutcome(
            EvaluationOutcome.pass(
                offerEvaluationStage,
                Collections.singletonList(offerRecommendation),
                "Decreased '%s': for resource: '%s' with resourceId: '%s'",
                resourceSpec.getName(),
                resourceSpec,
                resourceId)
                .mesosResource(mesosResource)
                .build(),
            ResourceUtils.getResourceId(resource).get());
      }
    }
  }

  public static Optional<String> getRole(PodSpec podSpec) {
    return podSpec.getTasks().stream()
        .map(TaskSpec::getResourceSet)
        .flatMap(resourceSet -> resourceSet.getResources().stream())
        .map(ResourceSpec::getRole)
        .findFirst();
  }

  static void setProtos(
      PodInfoBuilder podInfoBuilder,
      Protos.Resource resource,
      Optional<String> taskName)
  {
    if (taskName.isPresent()) {
      Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName.get());
      taskBuilder.addResources(resource);
    } else {
      Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
      executorBuilder.addResources(resource);
    }
  }

  public static boolean isRunningExecutor(PodInfoBuilder podInfoBuilder, Protos.Offer offer) {
    if (!podInfoBuilder.getExecutorBuilder().isPresent()) {
      return false;
    }

    for (Protos.ExecutorID execId : offer.getExecutorIdsList()) {
      if (execId.equals(podInfoBuilder.getExecutorBuilder().get().getExecutorId())) {
        return true;
      }
    }

    return false;
  }

  static class ReserveEvaluationOutcome {
    private final EvaluationOutcome evaluationOutcome;

    private final String resourceId;

    ReserveEvaluationOutcome(EvaluationOutcome evaluationOutcome, String resourceId) {
      this.evaluationOutcome = evaluationOutcome;
      this.resourceId = resourceId;
    }

    EvaluationOutcome getEvaluationOutcome() {
      return evaluationOutcome;
    }

    Optional<String> getResourceId() {
      return Optional.ofNullable(resourceId);
    }
  }
}
