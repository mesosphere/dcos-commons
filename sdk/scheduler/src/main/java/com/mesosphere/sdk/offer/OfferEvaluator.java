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
import org.apache.mesos.scheduler.plan.PodInstanceRequirement;
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
            List<OfferRecommendation> recommendations = evaluateInternal(offerRequirement, offer);
            if (!recommendations.isEmpty()) {
                logger.info("- {}: passed resource requirements, returning {} recommendations: {}",
                        index + 1, recommendations.size(), TextFormat.shortDebugString(offer));
                return recommendations;
            } else {
                logger.info("- {}: did not pass resource requirements: {}",
                        index + 1, TextFormat.shortDebugString(offer));
            }
        }

        return Collections.emptyList();
    }

    private OfferRequirement getOfferRequirement(PodInstanceRequirement podInstanceRequirement)
            throws InvalidRequirementException {

        PodInstance podInstance = podInstanceRequirement.getPodInstance();
        Collection<String> tasksToLaunch = podInstanceRequirement.getTasksToLaunch();
        logger.info("Generating OfferRequirement for pod: {}, with tasks: {}", podInstance.getName(), tasksToLaunch);

        List<Protos.TaskInfo> taskInfos = TaskUtils.getTaskNames(podInstance).stream()
                .map(taskName -> stateStore.fetchTask(taskName))
                .filter(taskInfoOptional -> taskInfoOptional.isPresent())
                .map(taskInfoOptional -> taskInfoOptional.get())
                .collect(Collectors.toList());

        String podTaskNames = podInstance.getName() + ":" + tasksToLaunch;
        try {
            if (taskInfos.isEmpty()) {
                logger.info("Generating new requirement: {}", podTaskNames);
                return offerRequirementProvider.getNewOfferRequirement(podInstance, tasksToLaunch);
            } else {
                logger.info("Generating existing requirement: {}", podTaskNames);
                return offerRequirementProvider.getExistingOfferRequirement(podInstance, tasksToLaunch);
            }
        } catch (InvalidRequirementException e) {
            throw new InvalidRequirementException(e);
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
                logger.info("- {}: Fully passed placement constraint, " +
                        "{} resources remain for evaluation: {}",
                        index + 1, filteredCount, offer.getId().getValue());
                filteredOffers.add(offer);
            } else if (filteredCount > 0) {
                logger.info("- {}: Partially passed placement constraint, " +
                        "{} of {} resources remain for evaluation: {}",
                        index + 1, filteredCount, originalCount, offer.getId().getValue());
                filteredOffers.add(offer);
            } else {
                logger.info("- {}: Failed placement constraint for all {} resources, " +
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
                fulfilledExecutorRequirementOptional = FulfilledRequirement.fulfillRequirement(
                        execReq.getResourceRequirements(),
                        execReq.getDynamicPortRequirements(),
                        execReq.getNamedVIPPortRequirements(),
                        offer,
                        pool);

                if (!fulfilledExecutorRequirementOptional.isPresent()) {
                    logger.info("Offer: '{}' does not fulfill the executor Resource Requirements: '{}'",
                            offer.getId().getValue(), execReq.getResourceRequirements());
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
            Optional<FulfilledRequirement> fulfilledTaskRequirementOptional =
                    FulfilledRequirement.fulfillRequirement(
                            taskReq.getResourceRequirements(),
                            taskReq.getDynamicPortRequirements(),
                            taskReq.getNamedVIPPortRequirements(),
                            offer,
                            pool);

            if (!fulfilledTaskRequirementOptional.isPresent()) {
                logger.info("Offer: '{}' does not fulfill the task Resource Requirements: '{}'",
                        offer.getId().getValue(), taskReq.getResourceRequirements());
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
                Optional<MesosResource> mesResOptional = pool.consume(resReq);
                if (!mesResOptional.isPresent()) {
                    logger.warn("Failed to satisfy resource requirement: {}",
                            TextFormat.shortDebugString(resReq.getResource()));
                    return Optional.empty();
                }
                final MesosResource mesRes = mesResOptional.get();
                logger.info("Satisfying resource requirement: {}\nwith resource: {}",
                        TextFormat.shortDebugString(resReq.getResource()),
                        TextFormat.shortDebugString(mesRes.getResource()));

                Resource fulfilledResource = getFulfilledResource(resReq, mesRes);
                if (resReq.expectsResource()) {
                    logger.info("Expects Resource");
                    // Compute any needed resource pool consumption / release operations
                    // as well as any additional needed Mesos Operations.  In the case
                    // where a requirement has changed for an Atomic resource, no Operations
                    // can be performed because the resource is Atomic.
                    if (expectedValueChanged(resReq, mesRes) && !mesRes.isAtomic()) {
                        Value reserveValue = ValueUtils.subtract(resReq.getValue(), mesRes.getValue());
                        Value unreserveValue = ValueUtils.subtract(mesRes.getValue(), resReq.getValue());

                        if (ValueUtils.compare(unreserveValue, ValueUtils.getZero(unreserveValue.getType())) > 0) {
                            logger.info("Updates reserved resource with less reservation");
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
                            logger.info("Updates reserved resource with additional reservation");
                            Resource reserveResource = ResourceUtils.getDesiredResource(
                                    resReq.getRole(),
                                    resReq.getPrincipal(),
                                    resReq.getName(),
                                    reserveValue);

                            if (pool.consume(new ResourceRequirement(reserveResource)).isPresent()) {
                                reserveResource = ResourceUtils.setResourceId(reserveResource, resReq.getResourceId());
                                reserveRecommendations.add(new ReserveOfferRecommendation(offer, reserveResource));
                                fulfilledResource = getFulfilledResource(
                                        resReq, new MesosResource(resReq.getResource()));
                            } else {
                                logger.warn("Insufficient resources to increase resource usage.");
                                return Optional.empty();
                            }
                        }
                    }
                } else {
                    if (resReq.reservesResource()) {
                        logger.info("Reserves Resource");
                        reserveRecommendations.add(new ReserveOfferRecommendation(offer, fulfilledResource));
                    }

                    if (resReq.createsVolume()) {
                        logger.info("Creates Volume");
                        createRecommendations.add(new CreateOfferRecommendation(offer, fulfilledResource));
                    }
                }

                logger.info("Fulfilled resource: {}", TextFormat.shortDebugString(fulfilledResource));
                fulfilledResources.add(fulfilledResource);
            }

            for (DynamicPortRequirement dynamicPortRequirement : dynamicPortRequirements) {
                Optional<MesosResource> mesResOptional = pool.consume(dynamicPortRequirement);
                if (!mesResOptional.isPresent()) {
                    logger.warn("Failed to satisfy resource requirement: {}",
                            TextFormat.shortDebugString(dynamicPortRequirement.getResource()));
                    return Optional.empty();
                }
                final MesosResource mesRes = mesResOptional.get();
                logger.info("Satisfying resource requirement: {}\nwith resource: {}",
                        TextFormat.shortDebugString(dynamicPortRequirement.getResource()),
                        TextFormat.shortDebugString(mesRes.getResource()));

                Resource fulfilledResource = getFulfilledResource(dynamicPortRequirement, mesRes);
                if (dynamicPortRequirement.reservesResource()) {
                    logger.info("Reserves Resource");
                    reserveRecommendations.add(new ReserveOfferRecommendation(offer, fulfilledResource));
                }

                logger.info("Fulfilled resource: {}", TextFormat.shortDebugString(fulfilledResource));
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

    private static boolean expectedValueChanged(ResourceRequirement resReq, MesosResource mesRes) {
        return !ValueUtils.equal(resReq.getValue(), mesRes.getValue());
    }

    private static Resource getFulfilledResource(ResourceRequirement resReq, MesosResource mesRes) {
        Resource.Builder builder = Resource.newBuilder(mesRes.getResource());
        builder.setRole(resReq.getResource().getRole());

        Optional<ReservationInfo> resInfo = getFulfilledReservationInfo(resReq, mesRes);
        if (resInfo.isPresent()) {
            builder.setReservation(resInfo.get());
        }

        Optional<DiskInfo> diskInfo = getFulfilledDiskInfo(resReq, mesRes);
        if (diskInfo.isPresent()) {
            builder.setDisk(diskInfo.get());
        }

        return builder.build();
    }

    private static Optional<DiskInfo> getFulfilledDiskInfo(
            ResourceRequirement resReq, MesosResource mesRes) {
        if (!resReq.getResource().hasDisk()) {
            return Optional.empty();
        }

        DiskInfo.Builder builder = DiskInfo.newBuilder(resReq.getResource().getDisk());
        if (mesRes.getResource().getDisk().hasSource()) {
            builder.setSource(mesRes.getResource().getDisk().getSource());
        }

        Optional<Persistence> persistence = getFulfilledPersistence(resReq);
        if (persistence.isPresent()) {
            builder.setPersistence(persistence.get());
        }

        return Optional.of(builder.build());
    }

    private static Optional<ReservationInfo> getFulfilledReservationInfo(
            ResourceRequirement resReq, MesosResource mesRes) {
        if (!resReq.reservesResource()) {
            return Optional.empty();
        } else {
            return Optional.of(ReservationInfo
                    .newBuilder(resReq.getResource().getReservation())
                    .setLabels(ResourceUtils.setResourceId(
                            resReq.getResource().getReservation().getLabels(),
                            UUID.randomUUID().toString()))
                    .build());
        }
    }

    private static Optional<Persistence> getFulfilledPersistence(ResourceRequirement resReq) {
        if (!resReq.createsVolume()) {
            return Optional.empty();
        } else {
            return Optional.of(Persistence
                    .newBuilder(resReq.getResource().getDisk().getPersistence())
                    .setId(UUID.randomUUID().toString())
                    .build());
        }
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
