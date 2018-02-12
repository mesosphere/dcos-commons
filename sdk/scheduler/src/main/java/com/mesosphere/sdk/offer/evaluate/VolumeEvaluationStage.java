package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(VolumeEvaluationStage.class);
    private final VolumeSpec volumeSpec;
    private final Optional<String> persistenceId;
    private final String taskName;
    private final Optional<String> resourceId;
    private final boolean useDefaultExecutor;
    private final Optional<String> sourceRoot;

    public static VolumeEvaluationStage getNew(VolumeSpec volumeSpec, String taskName, boolean useDefaultExecutor) {
        return new VolumeEvaluationStage(
                volumeSpec,
                taskName,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                useDefaultExecutor);
    }

    public static VolumeEvaluationStage getExisting(
            VolumeSpec volumeSpec,
            String taskName,
            Optional<String> resourceId,
            Optional<String> persistenceId,
            Optional<String> sourceRoot,
            boolean useDefaultExecutor) {
        return new VolumeEvaluationStage(
                volumeSpec,
                taskName,
                resourceId,
                persistenceId,
                sourceRoot,
                useDefaultExecutor);
    }

    private VolumeEvaluationStage(
            VolumeSpec volumeSpec,
            String taskName,
            Optional<String> resourceId,
            Optional<String> persistenceId,
            Optional<String> sourceRoot,
            boolean useDefaultExecutor) {
        this.volumeSpec = volumeSpec;
        this.taskName = taskName;
        this.resourceId = resourceId;
        this.persistenceId = persistenceId;
        this.sourceRoot = sourceRoot;
        this.useDefaultExecutor = useDefaultExecutor;
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
        if (taskName == null && isRunningExecutor && resourceId.isPresent() && persistenceId.isPresent()) {
            // This is a volume on a running executor, so it isn't present in the offer, but we need to make sure to
            // add it to the ExecutorInfo.
            podInfoBuilder.setExecutorVolume(volumeSpec);

            Resource volume = PodInfoBuilder.getExistingExecutorVolume(
                    volumeSpec,
                    resourceId,
                    persistenceId,
                    sourceRoot,
                    useDefaultExecutor);
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
                            this,
                            volumeSpec,
                            resourceId,
                            mesosResourcePool);
            EvaluationOutcome evaluationOutcome = reserveEvaluationOutcome.getEvaluationOutcome();
            if (!evaluationOutcome.isPassing()) {
                return evaluationOutcome;
            }

            offerRecommendations.addAll(evaluationOutcome.getOfferRecommendations());
            mesosResource = evaluationOutcome.getMesosResource().get();
            resource = ResourceBuilder.fromSpec(
                    volumeSpec,
                    reserveEvaluationOutcome.getResourceId(),
                    persistenceId,
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
                    persistenceId,
                    Optional.of(mesosResource.getResource().getDisk().getSource().getMount().getRoot()))
                    .setValue(mesosResource.getValue())
                    .setMesosResource(mesosResource)
                    .build();

            if (!resourceId.isPresent()) {
                // Initial reservation of resources
                logger.info("    Resource '{}' requires a RESERVE operation", volumeSpec.getName());
                offerRecommendations.add(new ReserveOfferRecommendation(
                        mesosResourcePool.getOffer(),
                        resource));
            }
        }

        if (createsVolume()) {
            logger.info("    Resource '{}' requires a CREATE operation", volumeSpec.getName());
            offerRecommendations.add(new CreateOfferRecommendation(mesosResourcePool.getOffer(), resource));
        }

        logger.info("  Generated '{}' resource for task: [{}]",
                volumeSpec.getName(), TextFormat.shortDebugString(resource));
        OfferEvaluationUtils.setProtos(podInfoBuilder, resource, getTaskName());

        if (taskName == null && useDefaultExecutor) {
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

    private Optional<String> getTaskName() {
        return Optional.ofNullable(taskName);
    }
}
