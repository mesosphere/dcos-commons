package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.CreateOfferRecommendation;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.MesosResource;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ReserveOfferRecommendation;
import com.mesosphere.sdk.offer.ResourceBuilder;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.specification.VolumeSpec;

import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.fail;
import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement},
 * ensuring that it contains an appropriately-sized volume, and creating any necessary instances of
 * {@link com.mesosphere.sdk.offer.ReserveOfferRecommendation} and
 * {@link com.mesosphere.sdk.offer.CreateOfferRecommendation} as necessary.
 */
public class VolumeEvaluationStage implements OfferEvaluationStage {

  private final Logger logger;

  private final VolumeSpec volumeSpec;

  private final Collection<String> taskNames;

  private final Optional<String> resourceId;

  private final Optional<String> resourceNamespace;

  private final Optional<String> persistenceId;

  private final Optional<Protos.ResourceProviderID> providerId;

  private final Optional<Protos.Resource.DiskInfo.Source> diskSource;

  public static VolumeEvaluationStage getNew(
      VolumeSpec volumeSpec,
      Collection<String> taskNames,
      Optional<String> resourceNamespace)
  {
    return new VolumeEvaluationStage(
        volumeSpec,
        taskNames,
        Optional.empty(),
        resourceNamespace,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  public static VolumeEvaluationStage getExisting(
      VolumeSpec volumeSpec,
      Collection<String> taskNames,
      Optional<String> resourceId,
      Optional<String> resourceNamespace,
      Optional<String> persistenceId,
      Optional<Protos.ResourceProviderID> providerId,
      Optional<Protos.Resource.DiskInfo.Source> diskSource)
  {
    return new VolumeEvaluationStage(
        volumeSpec,
        taskNames,
        resourceId,
        resourceNamespace,
        persistenceId,
        providerId,
        diskSource);
  }

  private VolumeEvaluationStage(
      VolumeSpec volumeSpec,
      Collection<String> taskNames,
      Optional<String> resourceId,
      Optional<String> resourceNamespace,
      Optional<String> persistenceId,
      Optional<Protos.ResourceProviderID> providerId,
      Optional<Protos.Resource.DiskInfo.Source> diskSource)
  {
    this.logger = LoggingUtils.getLogger(getClass(), resourceNamespace);
    this.volumeSpec = volumeSpec;
    this.taskNames = taskNames;
    this.resourceId = resourceId;
    this.resourceNamespace = resourceNamespace;
    this.persistenceId = persistenceId;
    this.providerId = providerId;
    this.diskSource = diskSource;
  }

  private boolean createsVolume() {
    return !persistenceId.isPresent();
  }

  @Override
  public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
    List<OfferRecommendation> offerRecommendations = new ArrayList<>();
    Protos.Resource resource;
    final MesosResource mesosResource;

    boolean isRunningExecutor =
        OfferEvaluationUtils.isRunningExecutor(podInfoBuilder, mesosResourcePool.getOffer());
    if (taskNames.isEmpty() && isRunningExecutor && resourceId.isPresent() && persistenceId.isPresent()) {
      // This is a volume on a running executor, so it isn't present in the offer, but we need to make sure to
      // add it to the ExecutorInfo.
      podInfoBuilder.setExecutorVolume(volumeSpec);

      Protos.Resource volume = PodInfoBuilder.getExistingExecutorVolume(
          volumeSpec,
          resourceId,
          resourceNamespace,
          persistenceId,
          providerId,
          diskSource);
      podInfoBuilder.getExecutorBuilder().get().addResources(volume);

      return pass(
          this,
          Collections.emptyList(),
          "Setting info for already running Executor with existing volume " +
              "with resourceId: '%s' and persistenceId: '%s'",
          resourceId,
          persistenceId)
          .build();
    }

    if (volumeSpec.getType().equals(VolumeSpec.Type.ROOT)) {
      OfferEvaluationUtils.ReserveEvaluationOutcome reserveEvaluationOutcome =
          OfferEvaluationUtils.evaluateSimpleResource(
              logger, this, volumeSpec, resourceId, resourceNamespace, mesosResourcePool);
      EvaluationOutcome evaluationOutcome = reserveEvaluationOutcome.getEvaluationOutcome();
      if (!evaluationOutcome.isPassing()) {
        return evaluationOutcome;
      }

      offerRecommendations.addAll(evaluationOutcome.getOfferRecommendations());
      mesosResource = evaluationOutcome.getMesosResource().get();
      resource = ResourceBuilder.fromSpec(
          volumeSpec,
          reserveEvaluationOutcome.getResourceId(),
          resourceNamespace,
          persistenceId,
          Optional.empty(),
          Optional.empty())
          .setMesosResource(mesosResource)
          .build();
    } else {
      Optional<MesosResource> mesosResourceOptional;
      if (!resourceId.isPresent()) {
        mesosResourceOptional =
            mesosResourcePool.consumeAtomic(Constants.DISK_RESOURCE_TYPE, volumeSpec);
      } else {
        mesosResourceOptional =
            mesosResourcePool.getReservedResourceById(resourceId.get());
      }

      if (!mesosResourceOptional.isPresent()) {
        return fail(this, "Failed to find MOUNT volume for '%s'.", volumeSpec).build();
      }

      mesosResource = mesosResourceOptional.get();

      resource = ResourceBuilder.fromSpec(
          volumeSpec,
          resourceId,
          resourceNamespace,
          persistenceId,
          ResourceUtils.getProviderId(mesosResource.getResource()),
          ResourceUtils.getDiskSource(mesosResource.getResource()))
          .setValue(mesosResource.getValue())
          .setMesosResource(mesosResource)
          .build();

      if (!resourceId.isPresent()) {
        // Initial reservation of resources
        logger.info("Resource '{}' requires a RESERVE operation", volumeSpec.getName());
        offerRecommendations.add(new ReserveOfferRecommendation(
            mesosResourcePool.getOffer(),
            resource));
      }
    }

    if (createsVolume()) {
      logger.info("Resource '{}' requires a CREATE operation", volumeSpec.getName());
      offerRecommendations.add(new CreateOfferRecommendation(mesosResourcePool.getOffer(), resource));
    }

    logger.info("Generated '{}' resource for tasks {}: [{}]",
        volumeSpec.getName(), taskNames, TextFormat.shortDebugString(resource));
    // If it's a task-level volume, add it to all tasks in the resource set:
    for (String taskName : taskNames) {
      OfferEvaluationUtils.setProtos(podInfoBuilder, resource, Optional.of(taskName));
    }
    // If it's an executor-level volume, add it to the executor:
    if (taskNames.isEmpty()) {
      OfferEvaluationUtils.setProtos(podInfoBuilder, resource, Optional.empty());
      podInfoBuilder.setExecutorVolume(volumeSpec);
    }

    if (resourceId.isPresent()) {
      return pass(
          this,
          offerRecommendations,
          "Offer contains previously reserved 'disk' with resourceId: '%s' and persistenceId: '%s' " +
              "for resource: '%s'",
          resourceId.get(),
          persistenceId, // in case persistenceId is unset, even if resourceId is set
          volumeSpec)
          .mesosResource(mesosResource)
          .build();
    } else {
      return pass(
          this,
          offerRecommendations,
          "Offer contains sufficient unreserved 'disk', generated new resourceId: '%s' " +
              "for new reservation: '%s'",
          ResourceUtils.getResourceId(resource).get(),
          volumeSpec)
          .mesosResource(mesosResource)
          .build();
    }
  }
}
