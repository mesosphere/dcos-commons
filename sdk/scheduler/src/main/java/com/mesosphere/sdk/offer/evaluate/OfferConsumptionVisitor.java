package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.CreateOfferRecommendation;
import com.mesosphere.sdk.offer.MesosResource;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.RangeUtils;
import com.mesosphere.sdk.offer.ReserveOfferRecommendation;
import com.mesosphere.sdk.offer.ResourceBuilder;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.UnreserveOfferRecommendation;
import com.mesosphere.sdk.offer.ValueUtils;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.DefaultPortSpec;
import com.mesosphere.sdk.specification.DefaultResourceSpec;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.fail;
import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * The OfferConsumptionVisitor traverses a {@link PodSpec} along with a {@link MesosResourcePool} and consumes resources
 * from that pool based on the requirements of that pod.
 */
public class OfferConsumptionVisitor extends SpecVisitor<EvaluationOutcome> {
    private static final Logger LOGGER = LoggerFactory.getLogger(OfferConsumptionVisitor.class);

    private final MesosResourcePool mesosResourcePool;
    private final ReservationCreator reservationCreator;
    private final List<EvaluationOutcome> evaluationOutcomes;
    private final Collection<Protos.TaskInfo> runningTasks;

    private PodInstanceRequirement podInstanceRequirement;
    private Map<String, Map<String, String>> visitedResourceSets;
    private String currentResourceSet;
    private boolean shouldConsumeResources;

    public OfferConsumptionVisitor(
            MesosResourcePool mesosResourcePool,
            ReservationCreator reservationCreator,
            Collection<Protos.TaskInfo> runningTasks,
            SpecVisitor delegate) {
        super(delegate);

        this.mesosResourcePool = mesosResourcePool;
        this.evaluationOutcomes = new ArrayList<>();
        this.reservationCreator = reservationCreator;
        this.visitedResourceSets = new HashMap<>();
        this.shouldConsumeResources = runningTasks.isEmpty();
        this.runningTasks = runningTasks;
    }

    @Override
    public PodInstanceRequirement visitImplementation(PodInstanceRequirement podInstanceRequirement) {
        this.podInstanceRequirement = podInstanceRequirement;
        Optional<Protos.ExecutorID> existingExecutorId = runningTasks.stream()
                .map(t -> t.getExecutor().getExecutorId())
                .findAny();

        if (!runningTasks.isEmpty() && existingExecutorId.isPresent() &&
                !mesosResourcePool.getOffer().getExecutorIdsList().contains(existingExecutorId.get())) {
            evaluationOutcomes.add(fail(
                    this,
                    "Offer does not contain the needed Executor ID: '%s'",
                    existingExecutorId.get()).build());
        }

        return podInstanceRequirement;
    }

    @Override
    public PodSpec visitImplementation(PodSpec podSpec) {
        return podSpec;
    }

    @Override
    public TaskSpec visitImplementation(TaskSpec taskSpec) {
        boolean resourceSetVisited = visitedResourceSets.containsKey(taskSpec.getResourceSet().getId());

        shouldConsumeResources = !(resourceSetVisited ||
                runningTasks.stream()
                        .map(t -> t.getName())
                        .anyMatch(t -> t.equals(
                                TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec))));
        currentResourceSet = taskSpec.getResourceSet().getId();

        if (!resourceSetVisited) {
            visitedResourceSets.put(taskSpec.getResourceSet().getId(), new HashMap<>());
        }

        return taskSpec;
    }

    @Override
    public TaskSpec finalizeImplementation(TaskSpec taskSpec) {
        shouldConsumeResources = runningTasks.isEmpty();
        currentResourceSet = null;

        return taskSpec;
    }

    @Override
    public ResourceSpec visitImplementation(ResourceSpec resourceSpec) {
        if (!shouldConsumeResources && currentResourceSet != null) {
            // We've already consumed the resources from the resource set that this resource belongs to, so don't
            // attempt to consume again.
            return withResource(
                    resourceSpec,
                    reservationCreator.withResourceId(
                            resourceSpec.getResource(),
                            getVisitedResourceId(resourceSpec)));
        } else if (!shouldConsumeResources) {
            return resourceSpec;
        }

        Optional<String> resourceId = ResourceUtils.getResourceId(resourceSpec.getResource());
        Optional<MesosResource> mesosResourceOptional = consume(resourceSpec, resourceId, mesosResourcePool);
        if (!mesosResourceOptional.isPresent()) {
            evaluationOutcomes.add(fail(
                    this,
                    "Offer failed to satisfy: %s with resourceId: %s",
                    resourceSpec.toString(),
                    resourceId).build());

            return resourceSpec;
        }

        OfferRecommendation offerRecommendation;
        MesosResource mesosResource = mesosResourceOptional.get();

        if (ValueUtils.equal(mesosResource.getValue(), resourceSpec.getValue())) {
            LOGGER.info("    Resource '{}' matches required value: {}",
                    resourceSpec.getName(),
                    TextFormat.shortDebugString(mesosResource.getValue()),
                    TextFormat.shortDebugString(resourceSpec.getValue()));

            if (!resourceId.isPresent()) {
                // Initial reservation of resources
                LOGGER.info("    Resource '{}' requires a RESERVE operation", resourceSpec.getName());
                Protos.Resource.Builder resourceBuilder = reservationCreator.withNewResourceId(
                        resourceSpec.getResource());
                resourceSpec = withResource(resourceSpec, resourceBuilder);
                recordReservationId(resourceSpec);
                offerRecommendation = new ReserveOfferRecommendation(
                        mesosResourcePool.getOffer(), resourceSpec.getResource());
                evaluationOutcomes.add(pass(
                        this,
                        Arrays.asList(offerRecommendation),
                        "Offer contains sufficient '%s': for resource: '%s' with resourceId: '%s'",
                        resourceSpec.getName(),
                        resourceSpec.toString(),
                        resourceId)
                        .build());
            } else {
                evaluationOutcomes.add(pass(
                        this,
                        Collections.emptyList(),
                        "Offer contains sufficient previously reserved '%s':" +
                                " for resource: '%s' with resourceId: '%s'",
                        resourceSpec.getName(),
                        resourceSpec,
                        resourceId)
                        .build());
            }
        } else {
            Protos.Value difference = ValueUtils.subtract(resourceSpec.getValue(), mesosResource.getValue());
            if (ValueUtils.compare(difference, ValueUtils.getZero(difference.getType())) > 0) {
                LOGGER.info("    Reservation for resource '{}' needs increasing from current {} to required {}",
                        resourceSpec.getName(),
                        TextFormat.shortDebugString(mesosResource.getValue()),
                        TextFormat.shortDebugString(resourceSpec.getValue()));

                ResourceSpec requiredAdditionalResources = DefaultResourceSpec.newBuilder(resourceSpec)
                        .value(difference)
                        .build();
                mesosResourceOptional = mesosResourcePool.consumeReservableMerged(
                        requiredAdditionalResources.getName(),
                        requiredAdditionalResources.getValue(),
                        Constants.ANY_ROLE);

                if (!mesosResourceOptional.isPresent()) {
                    evaluationOutcomes.add(fail(
                            this,
                            "Insufficient resources to increase reservation of resource '%s' with resourceId",
                            resourceSpec,
                            resourceId)
                            .build());
                }

                // Reservation of additional resources
                offerRecommendation = new ReserveOfferRecommendation(
                        mesosResourcePool.getOffer(), resourceSpec.getResource());
                evaluationOutcomes.add(pass(
                        this,
                        Arrays.asList(offerRecommendation),
                        "Offer contains sufficient '%s': for increasing resource: '%s' with resourceId: '%s'",
                        resourceSpec.getName(),
                        resourceSpec,
                        resourceId)
                        .build());
            } else {
                LOGGER.info("    Reservation for resource '%s' needs decreasing from current %s to required {}",
                        resourceSpec.getName(),
                        TextFormat.shortDebugString(mesosResource.getValue()),
                        TextFormat.shortDebugString(resourceSpec.getValue()));

                Protos.Value unreserve = ValueUtils.subtract(mesosResource.getValue(), resourceSpec.getValue());
                Protos.Resource resource = ResourceBuilder.fromSpec(resourceSpec, resourceId)
                        .setValue(unreserve)
                        .build();
                // Unreservation of no longer needed resources
                offerRecommendation = new UnreserveOfferRecommendation(
                        mesosResourcePool.getOffer(),
                        resource);
                evaluationOutcomes.add(pass(
                        this,
                        Arrays.asList(offerRecommendation),
                        "Decreased '%s': for resource: '%s' with resourceId: '%s'",
                        resourceSpec.getName(),
                        resourceSpec,
                        resourceId)
                        .build());
            }
        }

        return resourceSpec;
    }

    @Override
    public VolumeSpec visitImplementation(VolumeSpec volumeSpec) {
        if (!shouldConsumeResources && currentResourceSet != null) {
            // We've already consumed the resources from the resource set that this resource belongs to, so don't
            // attempt to consume again.
            return withResource(
                    volumeSpec,
                    reservationCreator.withResourceId(
                            volumeSpec.getResource(),
                            getVisitedResourceId(volumeSpec)));
        } else if (!shouldConsumeResources) {
            return volumeSpec;
        }
        Optional<String> resourceId = ResourceUtils.getResourceId(volumeSpec.getResource());
        Optional<String> persistenceId = ResourceUtils.getPersistenceId(volumeSpec.getResource());
        String detailsClause = resourceId.isPresent() ? "previously reserved " : "";

        Optional<MesosResource> mesosResourceOptional;
        if (volumeSpec.getType().equals(VolumeSpec.Type.ROOT)) {
            ResourceSpec reserved = visitImplementation((ResourceSpec) volumeSpec);
            Optional<String> reservedResourceId = ResourceUtils.getResourceId(reserved.getResource());

            if (reservedResourceId.isPresent()) {
                volumeSpec = withResource(
                        volumeSpec,
                        reservationCreator.withResourceId(
                                volumeSpec.getResource(), ResourceUtils.getResourceId(reserved.getResource()).get()));
            }
        } else {
            if (!resourceId.isPresent()) {
                mesosResourceOptional =
                        mesosResourcePool.consumeAtomic(Constants.DISK_RESOURCE_TYPE, volumeSpec.getValue());
            } else {
                mesosResourceOptional =
                        mesosResourcePool.getReservedResourceById(resourceId.get());
            }

            if (!mesosResourceOptional.isPresent()) {
                evaluationOutcomes.add(fail(this, "Failed to find MOUNT volume for '%s'.", volumeSpec).build());
            }
        }

        if (!persistenceId.isPresent()) {
            LOGGER.info("    Resource '{}' requires a CREATE operation", volumeSpec.getName());
            Protos.Resource.Builder resourceBuilder = reservationCreator.withNewResourceId(
                    reservationCreator.withPersistenceId(volumeSpec.getResource()));
            volumeSpec = withResource(volumeSpec, resourceBuilder);
            OfferRecommendation createRecommendation = new CreateOfferRecommendation(
                    mesosResourcePool.getOffer(), resourceBuilder.build());
            evaluationOutcomes.add(pass(
                    this,
                    Arrays.asList(createRecommendation),
                    "Offer has sufficient %s'disk': for resource: '%s' with resourceId: '%s' and persistenceId: '%s'",
                    detailsClause, volumeSpec, resourceId, persistenceId).build());
        }

        return volumeSpec;
    }

    @Override
    public PortSpec visitImplementation(PortSpec portSpec) {
        Long assignedPort = portSpec.getPort();
        PortSpec assignedPortSpec;

        if (assignedPort == 0) {
            assignedPort = mesosResourcePool.getUnassignedPort(podInstanceRequirement.getPodInstance().getPod());
            if (assignedPort == null) {
                evaluationOutcomes.add(fail(
                        this,
                        "No ports were available for dynamic claim in offer," +
                                " and no matching port %s was present in prior task: %s %s",
                        portSpec.getPortName(),
                        TextFormat.shortDebugString(mesosResourcePool.getOffer()))
                        .build());
                return portSpec;
            }

            Protos.Value value = Protos.Value.newBuilder()
                            .setType(Protos.Value.Type.RANGES)
                            .setRanges(RangeUtils.fromSingleValue(assignedPort))
                            .build();
            assignedPortSpec = new DefaultPortSpec(
                    value,
                    portSpec.getRole(),
                    portSpec.getPreReservedRole(),
                    portSpec.getPrincipal(),
                    portSpec.getEnvKey().isPresent() ? portSpec.getEnvKey().get() : null,
                    portSpec.getPortName(),
                    portSpec.getVisibility(),
                    portSpec.getNetworkNames()) {

                @Override
                public Protos.Resource.Builder getResource() {
                    return portSpec.getResource().setRanges(value.getRanges());
                }
            };
        } else {
            assignedPortSpec = portSpec;
        }

        if (assignedPortSpec.requiresHostPorts()) {
            return withResource(assignedPortSpec, visitImplementation((ResourceSpec) assignedPortSpec).getResource());
        } else {
            evaluationOutcomes.add(pass(
                    this,
                    "Port %s doesn't require resource reservation, ignoring resource requirements and using port %d",
                    assignedPortSpec.getPortName(),
                    assignedPort)
                    .build());
        }

        return assignedPortSpec;
    }

    @Override
    public NamedVIPSpec visitImplementation(NamedVIPSpec namedVIPSpec) throws SpecVisitorException {
        PortSpec visitedPortSpec = visitImplementation((PortSpec) namedVIPSpec);

        return new NamedVIPSpec(
                namedVIPSpec.getValue(),
                namedVIPSpec.getRole(),
                namedVIPSpec.getPreReservedRole(),
                namedVIPSpec.getPrincipal(),
                namedVIPSpec.getEnvKey().isPresent() ? namedVIPSpec.getEnvKey().get() : null,
                namedVIPSpec.getPortName(),
                namedVIPSpec.getProtocol(),
                namedVIPSpec.getVisibility(),
                namedVIPSpec.getVipName(),
                namedVIPSpec.getVipPort(),
                namedVIPSpec.getNetworkNames()) {

            @Override
            public Protos.Resource.Builder getResource() {
                return visitedPortSpec.getResource();
            }
        };
    }

    @Override
    Collection<EvaluationOutcome> getResultImplementation() {
        return evaluationOutcomes;
    }

    private static Optional<MesosResource> consume(
            ResourceSpec resourceSpec,
            Optional<String> resourceId,
            MesosResourcePool pool) {
        if (!resourceId.isPresent()) {
            return pool.consumeReservableMerged(
                    resourceSpec.getName(),
                    resourceSpec.getValue(),
                    resourceSpec.getPreReservedRole());
        } else {
            return pool.consumeReserved(resourceSpec.getName(), resourceSpec.getValue(), resourceId.get());
        }
    }

    private void recordReservationId(ResourceSpec resourceSpec) {
        Optional<String> resourceId = ResourceUtils.getResourceId(resourceSpec.getResource());
        if (currentResourceSet != null && resourceId.isPresent()) {
            String key = (resourceSpec instanceof VolumeSpec) ?
                    ((VolumeSpec) resourceSpec).getContainerPath() :
                    resourceSpec.getName();
            visitedResourceSets.get(currentResourceSet).put(key, resourceId.get());
        }
    }

    private String getVisitedResourceId(ResourceSpec resourceSpec) {
        if (currentResourceSet == null) {
            return null;
        }

        Optional<String> resourceId = ResourceUtils.getResourceId(resourceSpec.getResource());
        if (resourceId.isPresent()) {
            return resourceId.get();
        }

        String key = (resourceSpec instanceof VolumeSpec) ?
                ((VolumeSpec) resourceSpec).getContainerPath() :
                resourceSpec.getName();

        return visitedResourceSets.get(currentResourceSet).get(key);
    }
}
