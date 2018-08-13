package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.ResourceProviderID;
import org.slf4j.Logger;

import java.util.*;

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
    private final Optional<String> taskName;
    private final Optional<String> resourceId;
    private final Optional<String> resourceNamespace;
    private final Optional<String> persistenceId;
    private final Optional<ResourceProviderID> providerId;
    private final Optional<Resource.DiskInfo.Source> diskSource;

    public static VolumeEvaluationStage getNew(
            VolumeSpec volumeSpec,
            Optional<String> taskName,
            Optional<String> resourceNamespace) {
        return new VolumeEvaluationStage(
                volumeSpec,
                taskName,
                Optional.empty(),
                resourceNamespace,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public static VolumeEvaluationStage getExisting(
            VolumeSpec volumeSpec,
            Optional<String> taskName,
            Optional<String> resourceId,
            Optional<String> resourceNamespace,
            Optional<String> persistenceId,
            Optional<ResourceProviderID> providerId,
            Optional<Resource.DiskInfo.Source> diskSource) {
        return new VolumeEvaluationStage(
                volumeSpec,
                taskName,
                resourceId,
                resourceNamespace,
                persistenceId,
                providerId,
                diskSource);
    }

    private VolumeEvaluationStage(
            VolumeSpec volumeSpec,
            Optional<String> taskName,
            Optional<String> resourceId,
            Optional<String> resourceNamespace,
            Optional<String> persistenceId,
            Optional<ResourceProviderID> providerId,
            Optional<Resource.DiskInfo.Source> diskSource) {
        this.logger = LoggingUtils.getLogger(getClass(), resourceNamespace);
        this.volumeSpec = volumeSpec;
        this.taskName = taskName;
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
        String detailsClause = resourceId.isPresent() ? "previously reserved " : "";

        List<OfferRecommendation> offerRecommendations = new ArrayList<>();
        Resource resource;
        final MesosResource mesosResource;

        boolean isRunningExecutor =
                OfferEvaluationUtils.isRunningExecutor(podInfoBuilder, mesosResourcePool.getOffer());
        if (!taskName.isPresent() && isRunningExecutor && resourceId.isPresent() && persistenceId.isPresent()) {
            // This is a volume on a running executor, so it isn't present in the offer, but we need to make sure to
            // add it to the ExecutorInfo.
            podInfoBuilder.setExecutorVolume(volumeSpec);

            Resource volume = PodInfoBuilder.getExistingExecutorVolume(
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
                        mesosResourcePool.consumeAtomic(Constants.DISK_RESOURCE_TYPE, volumeSpec.getValue());
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

        logger.info("Generated '{}' resource for task: [{}]",
                volumeSpec.getName(), TextFormat.shortDebugString(resource));
        OfferEvaluationUtils.setProtos(podInfoBuilder, resource, taskName);

        if (!taskName.isPresent()) {
            podInfoBuilder.setExecutorVolume(volumeSpec);
        }

        return pass(
                this,
                offerRecommendations,
                "Offer contains sufficient %s'disk': for resource: '%s' with resourceId: '%s' and persistenceId: '%s'",
                detailsClause,
                volumeSpec,
                resourceId,
                persistenceId)
                .mesosResource(mesosResource)
                .build();
    }
}
