package com.mesosphere.sdk.offer;

import com.google.inject.Inject;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.executor.ExecutorUtils;
import com.mesosphere.sdk.offer.constrain.PlacementRule;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.Resource.ReservationInfo;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The OfferEvaluator processes {@link Offer}s and produces {@link OfferRecommendation}s.
 * The determination of what {@link OfferRecommendation}s, if any should be made are made
 * in reference to the {@link OfferRequirement with which it was constructed.  In the
 * case where an OfferRequirement has not been provided no {@link OfferRecommendation}s
 * are ever returned.
 */
public class OfferEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(OfferEvaluator.class);

    private final StateStore stateStore;
    private final OfferRequirementProvider offerRequirementProvider;

    @Inject
    public OfferEvaluator(StateStore stateStore, OfferRequirementProvider offerRequirementProvider) {
        this.stateStore = stateStore;
        this.offerRequirementProvider = offerRequirementProvider;
    }

    public List<OfferRecommendation> evaluate(PodInstanceRequirement podInstanceRequirement, List<Offer> offers)
            throws StateStoreException, InvalidRequirementException {
        return evaluate(getOfferRequirement(podInstanceRequirement), offers);
    }

    public List<OfferRecommendation> evaluate(OfferRequirement offerRequirement, List<Offer> offers) {
        // First, check placement constraints (to filter offers)
        List<Offer> filteredOffers = new ArrayList<>();
        Optional<PlacementRule> placementRuleOptional = offerRequirement.getPlacementRuleOptional();
        if (placementRuleOptional.isPresent()) {
            filteredOffers = evaluatePlacementRule(placementRuleOptional.get(), offerRequirement, offers);
            if (filteredOffers.isEmpty()) {
                logger.info("No offers survived placement constraint evaluation, skipping resource evaluation.");
                return Collections.emptyList();
            }
        } else {
            // No filtering, all offers pass:
            filteredOffers.addAll(offers);
        }

        // Then perform offer resource evaluation against the placement-filtered result.
        logger.info("Evaluating up to {} offers for match against resource requirements:", filteredOffers.size());
        for (int index = 0; index < filteredOffers.size(); ++index) {
            Offer offer = filteredOffers.get(index);
            logger.info("*** Offer {} ({}) evaluation:", index + 1, offer.getId().getValue());
            List<OfferRecommendation> recommendations = evaluateInternal(offerRequirement, offer);
            if (!recommendations.isEmpty()) {
                logger.info("*** Offer {}: Passed resource requirements, {} operations needed to proceed",
                        index + 1, recommendations.size());
                return recommendations;
            } else {
                logger.info("*** Offer {}: Did not pass resource requirements: {}",
                        index + 1, TextFormat.shortDebugString(offer));
            }
        }

        return Collections.emptyList();
    }

    private OfferRequirement getOfferRequirement(PodInstanceRequirement podInstanceRequirement)
            throws InvalidRequirementException {
        PodInstance podInstance = podInstanceRequirement.getPodInstance();
        boolean noTasksExist = TaskUtils.getTaskNames(podInstance).stream()
                .map(taskName -> stateStore.fetchTask(taskName))
                .filter(taskInfoOptional -> taskInfoOptional.isPresent())
                .map(taskInfoOptional -> taskInfoOptional.get())
                .count() == 0;

        final String description;
        final boolean shouldGetNewRequirement;
        if (podInstanceRequirement.isPermanentReplacement()) {
            description = "failed";
            shouldGetNewRequirement = true;
        } else if (noTasksExist) {
            description = "new";
            shouldGetNewRequirement = true;
        } else {
            description = "existing";
            shouldGetNewRequirement = false;
        }
        Collection<String> tasksToLaunch = podInstanceRequirement.getTasksToLaunch();
        logger.info("Generating requirement for {} pod '{}' containing tasks: {}",
                description, podInstance.getName(), tasksToLaunch);
        if (shouldGetNewRequirement) {
            return offerRequirementProvider.getNewOfferRequirement(podInstance, tasksToLaunch);
        } else {
            return offerRequirementProvider.getExistingOfferRequirement(podInstance, tasksToLaunch);
        }
    }

    private List<Offer> evaluatePlacementRule(
            PlacementRule placementRule, OfferRequirement offerRequirement, List<Offer> offers) {
        // Just fetch the tasks once, they shouldn't change within this evaluate() call:
        Collection<TaskInfo> tasks = stateStore.fetchTasks();
        // The reference PlacementRules all have custom toString()s, so this should give a good
        // representation of the filter:
        logger.info("Evaluating {} offers against placement constraint '{}':", offers.size(), placementRule);
        List<Offer> filteredOffers = new ArrayList<>();
        for (int index = 0; index < offers.size(); ++index) {
            // Pass offer with filtered resources removed:
            Offer offer = offers.get(index);
            int originalCount = offer.getResourcesCount();
            offer = placementRule.filter(offer, offerRequirement, tasks);
            int filteredCount = offer.getResourcesCount();
            if (filteredCount == originalCount) {
                logger.info("  {}: Fully passed placement constraint, " +
                        "{} resources remain for evaluation: {}",
                        index + 1, filteredCount, offer.getId().getValue());
                filteredOffers.add(offer);
            } else if (filteredCount > 0) {
                logger.info("  {}: Partially passed placement constraint, " +
                        "{} of {} resources remain for evaluation: {}",
                        index + 1, filteredCount, originalCount, offer.getId().getValue());
                filteredOffers.add(offer);
            } else {
                logger.info("  {}: Failed placement constraint for all {} resources, " +
                        "removed from resource evaluation",
                        index + 1, originalCount, offer.getId().getValue());
                // omit from filteredOffers
            }
        }
        return filteredOffers;
    }

    private List<OfferRecommendation> evaluateInternal(OfferRequirement offerRequirement, Offer offer) {

        MesosResourcePool pool = new MesosResourcePool(offer);

        List<OfferRecommendation> unreserves = new ArrayList<>();
        List<OfferRecommendation> reserves = new ArrayList<>();
        List<OfferRecommendation> creates = new ArrayList<>();
        List<OfferRecommendation> launches = new ArrayList<>();

        final Optional<ExecutorRequirement> execReqOptional =
                offerRequirement.getExecutorRequirementOptional();
        Optional<FulfilledRequirement> fulfilledExecutorRequirementOptional = Optional.empty();
        Optional<ExecutorInfo> execInfoOptional = Optional.empty();
        if (execReqOptional.isPresent()) {
            final ExecutorRequirement execReq = execReqOptional.get();
            execInfoOptional = Optional.of(execReq.getExecutorInfo());

            if (execReq.desiresResources()
                    || execInfoOptional.get().getExecutorId().getValue().isEmpty()) {
                logger.info("Evaluating offer '{}' against executor requirement...", offer.getId().getValue());
                fulfilledExecutorRequirementOptional = FulfilledRequirement.fulfillRequirement(
                        execReq.getResourceRequirements(),
                        execReq.getDynamicPortRequirements(),
                        execReq.getNamedVIPPortRequirements(),
                        offer,
                        pool);

                if (!fulfilledExecutorRequirementOptional.isPresent()) {
                    logger.info("Offer: '{}' does not fulfill executor requirement: '{}'",
                            offer.getId().getValue(), execReq);
                    return Collections.emptyList();
                }

                unreserves.addAll(fulfilledExecutorRequirementOptional.get().getUnreserveRecommendations());
                reserves.addAll(fulfilledExecutorRequirementOptional.get().getReserveRecommendations());
                creates.addAll(fulfilledExecutorRequirementOptional.get().getCreateRecommendations());
            } else {
                Protos.ExecutorID expectedExecutorId = execInfoOptional.get().getExecutorId();
                if (!hasExpectedExecutorId(offer, expectedExecutorId)) {
                    logger.info("Offer: '{}' does not contain the needed ExecutorID: '{}'",
                            offer.getId().getValue(), expectedExecutorId.getValue());
                    return Collections.emptyList();
                }
            }

            // Set executor ID *after* the other check above for its presence:
            if (execInfoOptional.get().getExecutorId().getValue().isEmpty()) {
                execInfoOptional = Optional.of(ExecutorInfo.newBuilder(execInfoOptional.get())
                        .setExecutorId(ExecutorUtils.toExecutorId(execInfoOptional.get().getName()))
                        .build());
            }
        }

        for (TaskRequirement taskReq : offerRequirement.getTaskRequirements()) {
            logger.info("Evaluating offer '{}' against task requirement...", offer.getId().getValue());
            Optional<FulfilledRequirement> fulfilledTaskRequirementOptional =
                    FulfilledRequirement.fulfillRequirement(
                            taskReq.getResourceRequirements(),
                            taskReq.getDynamicPortRequirements(),
                            taskReq.getNamedVIPPortRequirements(),
                            offer,
                            pool);

            if (!fulfilledTaskRequirementOptional.isPresent()) {
                logger.info("Offer: '{}' does not fulfill task requirement: '{}'",
                        offer.getId().getValue(), taskReq);
                return Collections.emptyList();
            }

            FulfilledRequirement fulfilledTaskRequirement = fulfilledTaskRequirementOptional.get();
            unreserves.addAll(fulfilledTaskRequirement.getUnreserveRecommendations());
            reserves.addAll(fulfilledTaskRequirement.getReserveRecommendations());
            creates.addAll(fulfilledTaskRequirement.getCreateRecommendations());

            launches.add(new LaunchOfferRecommendation(
                    offer,
                    getFulfilledTaskInfo(
                            taskReq,
                            fulfilledTaskRequirement,
                            execInfoOptional,
                            fulfilledExecutorRequirementOptional,
                            offer,
                            offerRequirement.getType(),
                            offerRequirement.getIndex())));
        }

        List<OfferRecommendation> recommendations = new ArrayList<>();
        recommendations.addAll(unreserves);
        recommendations.addAll(reserves);
        recommendations.addAll(creates);
        recommendations.addAll(launches);

        return recommendations;
    }

    private static class FulfilledRequirement {
        private List<Resource> fulfilledResources = new ArrayList<>();
        private List<OfferRecommendation> unreserveRecommendations = new ArrayList<>();
        private List<OfferRecommendation> reserveRecommendations = new ArrayList<>();
        private List<OfferRecommendation> createRecommendations = new ArrayList<>();

        private FulfilledRequirement(
                List<Resource> fulfilledResources,
                List<OfferRecommendation> unreserveRecommendations,
                List<OfferRecommendation> reserveRecommendations,
                List<OfferRecommendation> createRecommendations) {

            this.fulfilledResources = fulfilledResources;
            this.unreserveRecommendations = unreserveRecommendations;
            this.reserveRecommendations = reserveRecommendations;
            this.createRecommendations = createRecommendations;
        }

        public static Optional<FulfilledRequirement> fulfillRequirement(
                Collection<ResourceRequirement> resourceRequirements,
                Collection<DynamicPortRequirement> dynamicPortRequirements,
                Collection<NamedVIPPortRequirement> namedVIPPortRequirements,
                Offer offer,
                MesosResourcePool pool) {
            List<Resource> fulfilledResources = new ArrayList<>();
            List<OfferRecommendation> unreserveRecommendations = new ArrayList<>();
            List<OfferRecommendation> reserveRecommendations = new ArrayList<>();
            List<OfferRecommendation> createRecommendations = new ArrayList<>();

            // Regular resources and named VIP ports behave the same as far as resource consumption is concerned,
            // so handle them together.
            Collection<ResourceRequirement> resourceAndVIPPortRequirements = Stream.concat(
                    resourceRequirements.stream(), namedVIPPortRequirements.stream()).collect(Collectors.toList());
            for (ResourceRequirement resReq : resourceAndVIPPortRequirements) {
                logger.info("  Evaluating '{}'...", resReq.getName());

                Optional<MesosResource> mesResOptional = pool.consume(resReq);
                if (!mesResOptional.isPresent()) {
                    logger.warn("    Offered resources didn't have suitable '{}' for requirement: [{}]",
                            resReq.getName(),
                            TextFormat.shortDebugString(resReq.getResource()));
                    return Optional.empty();
                }
                final MesosResource mesRes = mesResOptional.get();
                logger.info("    Found suitable '{}':", resReq.getName());
                logger.info("      Required: [{}]", TextFormat.shortDebugString(resReq.getResource()));
                logger.info("      Offered: [{}]", TextFormat.shortDebugString(mesRes.getResource()));

                Resource fulfilledResource = getFulfilledResource(resReq, mesRes);
                if (resReq.expectsResource()) {
                    // Compute any needed resource pool consumption / release operations
                    // as well as any additional needed Mesos Operations.  In the case
                    // where a requirement has changed for an Atomic resource, no Operations
                    // can be performed because the resource is Atomic.
                    if (mesRes.isAtomic()) {
                        logger.info("    Resource '{}' is atomic and cannot be resized from current {} to required {}",
                                resReq.getName(),
                                TextFormat.shortDebugString(mesRes.getValue()),
                                TextFormat.shortDebugString(resReq.getValue()));
                    } else if (ValueUtils.equal(resReq.getValue(), mesRes.getValue())) {
                        logger.info("    Current reservation for resource '{}' matches required value: {}",
                                resReq.getName(), TextFormat.shortDebugString(resReq.getValue()));
                    } else {
                        logger.info("    Reservation for resource '{}' needs resizing from current {} to required {}",
                                resReq.getName(),
                                TextFormat.shortDebugString(mesRes.getValue()),
                                TextFormat.shortDebugString(resReq.getValue()));

                        Value reserveValue = ValueUtils.subtract(resReq.getValue(), mesRes.getValue());
                        Value unreserveValue = ValueUtils.subtract(mesRes.getValue(), resReq.getValue());

                        if (ValueUtils.compare(unreserveValue, ValueUtils.getZero(unreserveValue.getType())) > 0) {
                            logger.info("      => Decrease reservation of {} by {}",
                                    resReq.getName(), TextFormat.shortDebugString(unreserveValue));
                            Resource unreserveResource = ResourceUtils.getDesiredResource(
                                    resReq.getRole(),
                                    resReq.getPrincipal(),
                                    resReq.getName(),
                                    unreserveValue);
                            unreserveResource = ResourceUtils.setResourceId(unreserveResource, resReq.getResourceId());

                            pool.release(new MesosResource(
                                    ResourceUtils.getUnreservedResource(resReq.getName(), unreserveValue)));
                            unreserveRecommendations.add(new UnreserveOfferRecommendation(offer, unreserveResource));
                            fulfilledResource = getFulfilledResource(resReq, new MesosResource(resReq.getResource()));
                        }

                        if (ValueUtils.compare(reserveValue, ValueUtils.getZero(reserveValue.getType())) > 0) {
                            logger.info("      => Increase reservation of '{}' by {}",
                                    resReq.getName(), TextFormat.shortDebugString(reserveValue));
                            Resource reserveResource = ResourceUtils.getDesiredResource(
                                    resReq.getRole(),
                                    resReq.getPrincipal(),
                                    resReq.getName(),
                                    reserveValue);

                            if (pool.consume(new ResourceRequirement(reserveResource)).isPresent()) {
                                reserveResource = ResourceUtils.setResourceId(reserveResource, resReq.getResourceId());
                                reserveRecommendations.add(new ReserveOfferRecommendation(offer, reserveResource));
                                fulfilledResource =
                                        getFulfilledResource(resReq, new MesosResource(resReq.getResource()));
                            } else {
                                logger.warn("    Insufficient offered resources to increase reservation of '{}', " +
                                        "rejecting offer", resReq.getName());
                                return Optional.empty();
                            }
                        }
                    }
                } else {
                    if (resReq.reservesResource()) {
                        logger.info("    Resource '{}' requires a RESERVE operation", resReq.getName());
                        reserveRecommendations.add(new ReserveOfferRecommendation(offer, fulfilledResource));
                    }

                    if (resReq.createsVolume()) {
                        logger.info("    Resource '{}' requires a CREATE operation", resReq.getName());
                        createRecommendations.add(new CreateOfferRecommendation(offer, fulfilledResource));
                    }
                }

                logger.info("  Generated '{}' resource for task: [{}]",
                        resReq.getName(), TextFormat.shortDebugString(fulfilledResource));
                fulfilledResources.add(fulfilledResource);
            }

            for (DynamicPortRequirement dynamicPortRequirement : dynamicPortRequirements) {
                logger.info("  Evaluating dynamic port requirement for '{}'...", dynamicPortRequirement.getName());

                Optional<MesosResource> mesResOptional = pool.consume(dynamicPortRequirement);
                if (!mesResOptional.isPresent()) {
                    logger.warn("    Offered resources didn't have suitable '{}' for requirement: [{}]",
                            dynamicPortRequirement.getName(),
                            TextFormat.shortDebugString(dynamicPortRequirement.getResource()));
                    return Optional.empty();
                }
                final MesosResource mesRes = mesResOptional.get();
                logger.info("    Found suitable '{}' (dynamic port):", dynamicPortRequirement.getName());
                logger.info("      Required: [{}]", TextFormat.shortDebugString(dynamicPortRequirement.getResource()));
                logger.info("      Offered: [{}]", TextFormat.shortDebugString(mesRes.getResource()));

                Resource fulfilledResource = getFulfilledResource(dynamicPortRequirement, mesRes);
                if (dynamicPortRequirement.reservesResource()) {
                    logger.info("    Dynamic port resource '{}' requires a RESERVE operation",
                            dynamicPortRequirement.getName());
                    reserveRecommendations.add(new ReserveOfferRecommendation(offer, fulfilledResource));
                }

                logger.info("  Generated '{}' dynamic port resource for task: [{}]",
                        dynamicPortRequirement.getName(), TextFormat.shortDebugString(fulfilledResource));
                fulfilledResources.add(fulfilledResource);
            }

            return Optional.of(new FulfilledRequirement(
                    fulfilledResources,
                    unreserveRecommendations,
                    reserveRecommendations,
                    createRecommendations));
        }

        public List<Resource> getFulfilledResources() {
            return fulfilledResources;
        }

        public List<OfferRecommendation> getUnreserveRecommendations() {
            return unreserveRecommendations;
        }

        public List<OfferRecommendation> getReserveRecommendations() {
            return reserveRecommendations;
        }

        public List<OfferRecommendation> getCreateRecommendations() {
            return createRecommendations;
        }
    }

    private static boolean hasExpectedExecutorId(Offer offer, Protos.ExecutorID executorID) {
        for (Protos.ExecutorID execId : offer.getExecutorIdsList()) {
            if (execId.equals(executorID)) {
                return true;
            }
        }

        return false;
    }

    private static Resource getFulfilledResource(ResourceRequirement resReq, MesosResource mesRes) {
        Resource.Builder builder = Resource.newBuilder(mesRes.getResource());
        builder.setRole(resReq.getResource().getRole());

        if (resReq.reservesResource()) {
            builder.setReservation(ReservationInfo
                    .newBuilder(resReq.getResource().getReservation())
                    .setLabels(ResourceUtils.setResourceId(
                            resReq.getResource().getReservation().getLabels(),
                            UUID.randomUUID().toString())));
        }

        if (resReq.getResource().hasDisk()) {
            DiskInfo.Builder diskBuilder = DiskInfo.newBuilder(resReq.getResource().getDisk());
            if (mesRes.getResource().getDisk().hasSource()) {
                diskBuilder.setSource(mesRes.getResource().getDisk().getSource());
            }
            if (resReq.createsVolume()) {
                diskBuilder.setPersistence(Persistence
                        .newBuilder(resReq.getResource().getDisk().getPersistence())
                        .setId(UUID.randomUUID().toString()));
            }
            builder.setDisk(diskBuilder);
        }

        return builder.build();
    }

    private static TaskInfo getFulfilledTaskInfo(
            TaskRequirement taskReq,
            FulfilledRequirement fulfilledTaskRequirement,
            Optional<ExecutorInfo> execInfo,
            Optional<FulfilledRequirement> fulfilledExecutorRequirement,
            Offer launchOffer,
            String type,
            Integer index) {

        List<Resource> fulfilledTaskResources = fulfilledTaskRequirement.getFulfilledResources();
        TaskInfo.Builder taskBuilder = TaskInfo
                .newBuilder(taskReq.getTaskInfo())
                .clearResources()
                .addAllResources(fulfilledTaskResources);

        if (execInfo.isPresent()) {
            List<Resource> selectedResources = fulfilledExecutorRequirement.isPresent()
                    ? fulfilledExecutorRequirement.get().getFulfilledResources()
                    : execInfo.get().getResourcesList();
            ExecutorInfo.Builder execBuilder = ExecutorInfo
                    .newBuilder(execInfo.get())
                    .clearResources()
                    .addAllResources(selectedResources);
            execBuilder = ResourceUtils.updateEnvironment(execBuilder, selectedResources);
            execBuilder = ResourceUtils.setDiscoveryInfo(execBuilder, selectedResources);
            taskBuilder.setExecutor(execBuilder);
        }

        // Store metadata in the TaskInfo for later access by placement constraints:
        taskBuilder = CommonTaskUtils.setOfferAttributes(taskBuilder, launchOffer);
        taskBuilder = CommonTaskUtils.setType(taskBuilder, type);
        taskBuilder = CommonTaskUtils.setIndex(taskBuilder, index);
        taskBuilder = CommonTaskUtils.setHostname(taskBuilder, launchOffer);

        taskBuilder = ResourceUtils.setDiscoveryInfo(taskBuilder, fulfilledTaskResources);

        return CommonTaskUtils.packTaskInfo(
                ResourceUtils.updateEnvironment(taskBuilder, fulfilledTaskResources).build());
    }
}
