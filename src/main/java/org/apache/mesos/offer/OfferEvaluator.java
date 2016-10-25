package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.Resource.ReservationInfo;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.executor.ExecutorUtils;
import org.apache.mesos.offer.constrain.PlacementRule;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.state.StateStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.protobuf.TextFormat;

import java.util.*;

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

    @Inject
    public OfferEvaluator(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    public List<OfferRecommendation> evaluate(OfferRequirement offerRequirement, List<Offer> offers)
            throws StateStoreException {
        Optional<PlacementRule> placementRule = getPlacementRule(offerRequirement, stateStore);
        if (placementRule.isPresent()) {
            // The reference PlacementRules all have custom toString()s, so this should give a good
            // representation of the filter:
            logger.info("Evaluating {} offers against placement constraints: {}",
                    offers.size(), placementRule.get());
        }
        for (Offer offer : offers) {
            List<OfferRecommendation> recommendations =
                    evaluateInternal(offerRequirement, offer, placementRule);
            if (!recommendations.isEmpty()) {
                logger.info("Offer passed placement/resource requirements, produced {} recommendations: {}",
                        recommendations.size(), TextFormat.shortDebugString(offer));
                return recommendations;
            } else {
                logger.info("Offer did not pass constraints and/or resource requirements: {}",
                        TextFormat.shortDebugString(offer));
            }
        }
        return Collections.emptyList();
    }

    public List<OfferRecommendation> evaluate(OfferRequirement offerRequirement, Offer offer)
            throws StateStoreException {
        List<OfferRecommendation> recommendations =
                evaluateInternal(offerRequirement, offer, getPlacementRule(offerRequirement, stateStore));
        if (!recommendations.isEmpty()) {
            logger.info("Offer passed resource requirements, produced {} recommendations: {}",
                    recommendations.size(), TextFormat.shortDebugString(offer));
        } else {
            logger.info("Offer did not pass resource requirements: {}",
                    TextFormat.shortDebugString(offer));
        }
        return recommendations;
    }

    private List<OfferRecommendation> evaluateInternal(
            OfferRequirement offerRequirement, Offer offer, Optional<PlacementRule> placementRule) {
        if (placementRule.isPresent()) {
            int originalCount = offer.getResourcesCount();
            offer = placementRule.get().filter(offer);
            int filteredCount = offer.getResourcesCount();
            if (filteredCount == originalCount) {
                logger.info("Offer: '{}' fully passed placement constraints, evaluating {} of {} resources",
                        offer.getId().getValue(), filteredCount, originalCount);
            } else if (filteredCount > 0) {
                logger.info("Offer: '{}' partially passed placement constraints, evaluating {} of {} resources",
                        offer.getId().getValue(), filteredCount, originalCount);
            } else {
                logger.info("Offer: '{}' all {} resources didn't pass placement constraints, "
                                + "skipping offer resource evaluation and declining offer.",
                        offer.getId().getValue(), originalCount);
                return Collections.emptyList(); // short-circuit
            }
        }

        MesosResourcePool pool = new MesosResourcePool(offer);

        List<OfferRecommendation> unreserves = new ArrayList<>();
        List<OfferRecommendation> reserves = new ArrayList<>();
        List<OfferRecommendation> creates = new ArrayList<>();
        List<OfferRecommendation> launches = new ArrayList<>();

        final Optional<ExecutorRequirement> execReqOptional = offerRequirement.getExecutorRequirementOptional();
        Optional<FulfilledRequirement> fulfilledExecutorRequirementOptional = Optional.empty();
        Optional<ExecutorInfo> execInfoOptional = Optional.empty();
        if (execReqOptional.isPresent()) {
            final ExecutorRequirement execReq = execReqOptional.get();
            execInfoOptional = Optional.of(execReq.getExecutorInfo());

            if (execReq.desiresResources()
                    || execInfoOptional.get().getExecutorId().getValue().isEmpty()) {
                fulfilledExecutorRequirementOptional = FulfilledRequirement.fulfillRequirement(
                        execReq.getResourceRequirements(),
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
                            offerRequirement.getTaskType())));
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
                Offer offer,
                MesosResourcePool pool) {

            List<Resource> fulfilledResources = new ArrayList<>();
            List<OfferRecommendation> unreserveRecommendations = new ArrayList<>();
            List<OfferRecommendation> reserveRecommendations = new ArrayList<>();
            List<OfferRecommendation> createRecommendations = new ArrayList<>();

            for (ResourceRequirement resReq : resourceRequirements) {
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

    private static Optional<PlacementRule> getPlacementRule(
            OfferRequirement offerRequirement, StateStore stateStore)
            throws StateStoreException {
        Optional<PlacementRuleGenerator> placementRuleGenerator =
                offerRequirement.getPlacementRuleGeneratorOptional();
        return placementRuleGenerator.isPresent()
                ? Optional.of(placementRuleGenerator.get().generate(stateStore.fetchTasks()))
                : Optional.empty();
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

        Optional<ReservationInfo> resInfo = getFulfilledReservationInfo(resReq);
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

    private static Optional<ReservationInfo> getFulfilledReservationInfo(ResourceRequirement resReq) {
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
            String taskType) {

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
            //execBuilder = ResourceUtils.updateEnvironment2(execBuilder, fulfilledTaskRequirement);

            taskBuilder.setExecutor(execBuilder);
            taskBuilder.setExecutor(execBuilder);

            taskBuilder = ResourceUtils.setVIPDiscovery(taskBuilder, execInfo.get().getName(), fulfilledTaskResources);

        }

        // Store metadata in the TaskInfo for later access by placement constraints:
        taskBuilder = TaskUtils.setOfferAttributes(taskBuilder, launchOffer);
        taskBuilder = TaskUtils.setTaskType(taskBuilder, taskType);

        taskBuilder = ResourceUtils.updateEnvironment(taskBuilder, fulfilledTaskResources);
        //taskBuilder=ResourceUtils.updateEnvironment2(taskBuilder, fulfilledTaskRequirement);

        return TaskUtils.packTaskInfo(taskBuilder.build());
    }
}
