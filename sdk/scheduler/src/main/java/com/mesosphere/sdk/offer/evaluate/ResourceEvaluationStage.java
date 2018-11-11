package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.ReserveOfferRecommendation;
import com.mesosphere.sdk.offer.ResourceBuilder;
import com.mesosphere.sdk.offer.UnreserveOfferRecommendation;
import com.mesosphere.sdk.specification.ResourceSpec;

import org.apache.mesos.Protos.Resource;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement},
 * ensuring that it contains a sufficient amount or value of the supplied {@link Resource}, and creating a
 * {@link ReserveOfferRecommendation} or {@link UnreserveOfferRecommendation} where necessary.
 */
public class ResourceEvaluationStage implements OfferEvaluationStage {

  private final Logger logger;

  private final ResourceSpec resourceSpec;

  private final Collection<String> taskNames;

  private final Optional<String> requiredResourceId;

  private final Optional<String> resourceNamespace;

  /**
   * Creates a new instance for basic resource evaluation.
   *
   * @param resourceSpec       the resource spec to be evaluated
   * @param taskNames          the name of the tasks which will use this resource: multiple when they share a ResourceSet
   * @param requiredResourceId any previously reserved resource ID to be required, or empty for a new reservation
   * @param resourceNamespace  the namespace label, if any, to store in the resource
   */
  public ResourceEvaluationStage(
      ResourceSpec resourceSpec,
      Collection<String> taskNames,
      Optional<String> requiredResourceId,
      Optional<String> resourceNamespace)
  {
    this.logger = LoggingUtils.getLogger(getClass(), resourceNamespace);
    this.resourceSpec = resourceSpec;
    this.taskNames = taskNames;
    this.requiredResourceId = requiredResourceId;
    this.resourceNamespace = resourceNamespace;
  }

  @Override
  public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool,
                                    PodInfoBuilder podInfoBuilder)
  {
    boolean isRunningExecutor =
        OfferEvaluationUtils.isRunningExecutor(podInfoBuilder, mesosResourcePool.getOffer());
    if (taskNames.isEmpty() && isRunningExecutor && requiredResourceId.isPresent()) {
      // This is a resource on a running executor, so it isn't present in the offer, but we regardless need to
      // make sure to add it to the ExecutorInfo. This is because of the Mesos limitation that all tasks launched
      // in the same Executor must use an exact matching copy of the ExecutorInfo.

      OfferEvaluationUtils.setProtos(
          podInfoBuilder,
          ResourceBuilder.fromSpec(resourceSpec, requiredResourceId, resourceNamespace).build(),
          Optional.empty());
      return EvaluationOutcome.pass(
          this,
          Collections.emptyList(),
          "Including running executor's '%s' resource with resourceId: '%s': %s",
          resourceSpec.getName(),
          requiredResourceId.get(),
          resourceSpec)
          .build();
    }

    OfferEvaluationUtils.ReserveEvaluationOutcome reserveEvaluationOutcome =
        OfferEvaluationUtils.evaluateSimpleResource(
            logger,
            this,
            resourceSpec,
            requiredResourceId,
            resourceNamespace,
            mesosResourcePool);

    EvaluationOutcome evaluationOutcome = reserveEvaluationOutcome.getEvaluationOutcome();
    if (!evaluationOutcome.isPassing()) {
      return evaluationOutcome;
    }

    // Use the reservation outcome's resourceId, which is a newly generated UUID if requiredResourceId was empty.

    // Update every task that shares this resource. Multiple tasks may share a resource if they are in the same
    // resource set.
    for (String taskName : taskNames) {
      OfferEvaluationUtils.setProtos(
          podInfoBuilder,
          ResourceBuilder.fromSpec(
              resourceSpec, reserveEvaluationOutcome.getResourceId(), resourceNamespace).build(),
          Optional.of(taskName));
    }
    // If it's instead an executor-level resource, we need to update the (shared) executor info:
    if (taskNames.isEmpty()) {
      OfferEvaluationUtils.setProtos(
          podInfoBuilder,
          ResourceBuilder.fromSpec(
              resourceSpec, reserveEvaluationOutcome.getResourceId(), resourceNamespace).build(),
          Optional.empty());
    }

    return evaluationOutcome;
  }
}
