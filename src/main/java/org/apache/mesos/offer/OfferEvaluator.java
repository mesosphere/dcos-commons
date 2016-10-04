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

import com.google.protobuf.TextFormat;

import java.util.*;

/**
 * The OfferEvaluator processes {@link Offer}s and produces {@link OfferRecommendation}s.
 * The determination of what {@link OfferRecommendation}s, if any should be made are made
 * in reference to the {@link OfferRequirement with which it was constructed.    In the
 * case where an OfferRequirement has not been provided no {@link OfferRecommendation}s
 * are ever returned.
 */
public class OfferEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(OfferEvaluator.class);

    private final StateStore stateStore;

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
            if (recommendations != null && !recommendations.isEmpty()) {
                logger.info("Offer produced {} recommendations: {}",
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
        return evaluateInternal(
                offerRequirement, offer, getPlacementRule(offerRequirement, stateStore));
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
                logger.info("Offer: '{}' didn't pass placement constraints, short-circuiting {} resources",
                        offer.getId().getValue(), originalCount);
                return Collections.emptyList(); // short-circuit
            }
        }

        MesosResourcePool pool = new MesosResourcePool(offer);

        List<OfferRecommendation> unreserves = new ArrayList<>();
        List<OfferRecommendation> reserves = new ArrayList<>();
        List<OfferRecommendation> creates = new ArrayList<>();
        List<OfferRecommendation> launches = new ArrayList<>();

        Optional<ExecutorRequirement> execReq = offerRequirement.getExecutorRequirement();
        FulfilledRequirement fulfilledExecutorRequirement = null;
        Optional<ExecutorInfo> execInfo = Optional.empty();
        if (execReq.isPresent()) {
            if (execReq.get().desiresResources()
                    || execReq.get().getExecutorInfo().getExecutorId().getValue().isEmpty()) {
                fulfilledExecutorRequirement = FulfilledRequirement.fulfillRequirement(
                        execReq.get().getResourceRequirements(),
                        execReq.get().getDynamicPortRequirements(),
                        offer,
                        pool);

                if (fulfilledExecutorRequirement == null) {
                    return Collections.emptyList();
                }

                unreserves.addAll(fulfilledExecutorRequirement.getUnreserveRecommendations());
                reserves.addAll(fulfilledExecutorRequirement.getReserveRecommendations());
                creates.addAll(fulfilledExecutorRequirement.getCreateRecommendations());
            } else {
                Protos.ExecutorID expectedExecutorId = execReq.get().getExecutorInfo().getExecutorId();
                if (!hasExpectedExecutorId(offer, expectedExecutorId)) {
                    logger.info("Offer: '{}' does not contain the needed ExecutorID: '{}'",
                            offer.getId().getValue(), expectedExecutorId.getValue());
                    return Collections.emptyList();
                }
            }

            execInfo = Optional.of(execReq.get().getExecutorInfo());
            if (execInfo.get().getExecutorId().getValue().isEmpty()) {
                execInfo = Optional.of(ExecutorInfo.newBuilder(execInfo.get())
                        .setExecutorId(ExecutorUtils.toExecutorId(execInfo.get().getName()))
                        .build());
            }
        }

        for (TaskRequirement taskReq : offerRequirement.getTaskRequirements()) {
            FulfilledRequirement fulfilledTaskRequirement =
                    FulfilledRequirement.fulfillRequirement(
                            taskReq.getResourceRequirements(),
                            taskReq.getDynamicPortRequirements(),
                            offer,
                            pool);

            if (fulfilledTaskRequirement == null) {
                return Collections.emptyList();
            }

            unreserves.addAll(fulfilledTaskRequirement.getUnreserveRecommendations());
            reserves.addAll(fulfilledTaskRequirement.getReserveRecommendations());
            creates.addAll(fulfilledTaskRequirement.getCreateRecommendations());

            launches.add(
                    new LaunchOfferRecommendation(
                            offer,
                            getFulfilledTaskInfo(
                                    taskReq,
                                    fulfilledTaskRequirement,
                                    execInfo,
                                    fulfilledExecutorRequirement,
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

        public static FulfilledRequirement fulfillRequirement(
                Collection<ResourceRequirement> resourceRequirements,
                Collection<DynamicPortRequirement> dynamicPortRequirements,
                Offer offer,
                MesosResourcePool pool) {

            List<Resource> fulfilledResources = new ArrayList<>();
            List<OfferRecommendation> unreserveRecommendations = new ArrayList<>();
            List<OfferRecommendation> reserveRecommendations = new ArrayList<>();
            List<OfferRecommendation> createRecommendations = new ArrayList<>();

            for (ResourceRequirement resReq : resourceRequirements) {
                MesosResource mesRes = pool.consume(resReq);
                if (mesRes == null) {
                    logger.warn("Failed to satisfy resource requirement: {}",
                            TextFormat.shortDebugString(resReq.getResource()));
                    return null;
                } else {
                    logger.info("Satisfying resource requirement: {}\nwith resource: {}",
                            TextFormat.shortDebugString(resReq.getResource()),
                            TextFormat.shortDebugString(mesRes.getResource()));
                }

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

                            if (pool.consume(new ResourceRequirement(reserveResource)) != null) {
                                reserveResource = ResourceUtils.setResourceId(reserveResource, resReq.getResourceId());
                                reserveRecommendations.add(new ReserveOfferRecommendation(offer, reserveResource));
                                fulfilledResource = getFulfilledResource(
                                        resReq, new MesosResource(resReq.getResource()));
                            } else {
                                logger.warn("Insufficient resources to increase resource usage.");
                                return null;
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
                MesosResource mesRes = pool.consume(dynamicPortRequirement);
                if (mesRes == null) {
                    logger.warn("Failed to satisfy resource requirement: {}",
                            TextFormat.shortDebugString(dynamicPortRequirement.getResource()));
                    return null;
                } else {
                    logger.info("Satisfying resource requirement: {}\nwith resource: {}",
                            TextFormat.shortDebugString(dynamicPortRequirement.getResource()),
                            TextFormat.shortDebugString(mesRes.getResource()));
                }

                Resource fulfilledResource = getFulfilledResource(dynamicPortRequirement, mesRes);
                if (dynamicPortRequirement.reservesResource()) {
                    logger.info("Reserves Resource");
                    reserveRecommendations.add(new ReserveOfferRecommendation(offer, fulfilledResource));
                }

                logger.info("Fulfilled resource: {}", TextFormat.shortDebugString(fulfilledResource));
                fulfilledResources.add(fulfilledResource);
            }

            return new FulfilledRequirement(
                    fulfilledResources,
                    unreserveRecommendations,
                    reserveRecommendations,
                    createRecommendations);
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
                offerRequirement.getPlacementRuleGenerator();
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

        ReservationInfo resInfo = getFulfilledReservationInfo(resReq, mesRes);
        if (resInfo != null) {
            builder.setReservation(resInfo);
        }

        DiskInfo diskInfo = getFulfilledDiskInfo(resReq, mesRes);
        if (diskInfo != null) {
            builder.setDisk(diskInfo);
        }

        return builder.build();
    }

    private static ReservationInfo getFulfilledReservationInfo(ResourceRequirement resReq, MesosResource mesRes) {
        if (!resReq.reservesResource()) {
            return null;
        } else {
            ReservationInfo.Builder resBuilder = ReservationInfo.newBuilder(resReq.getResource().getReservation());
            resBuilder.setLabels(
                    ResourceUtils.setResourceId(
                            resReq.getResource().getReservation().getLabels(),
                            UUID.randomUUID().toString()));
            return resBuilder.build();
        }
    }

    private static DiskInfo getFulfilledDiskInfo(ResourceRequirement resReq, MesosResource mesRes) {
        if (!resReq.getResource().hasDisk()) {
            return null;
        }

        DiskInfo.Builder builder = DiskInfo.newBuilder(resReq.getResource().getDisk());
        if (mesRes.getResource().getDisk().hasSource()) {
            builder.setSource(mesRes.getResource().getDisk().getSource());
        }

        Persistence persistence = getFulfilledPersistence(resReq);
        if (persistence != null) {
            builder.setPersistence(persistence);
        }

        return builder.build();
    }

    private static Persistence getFulfilledPersistence(ResourceRequirement resReq) {
        if (!resReq.createsVolume()) {
            return null;
        } else {
            String persistenceId = UUID.randomUUID().toString();
            return Persistence.newBuilder(resReq.getResource().getDisk().getPersistence()).setId(persistenceId).build();
        }
    }

    private static TaskInfo getFulfilledTaskInfo(
            TaskRequirement taskReq,
            FulfilledRequirement fulfilledTaskRequirement,
            Optional<ExecutorInfo> execInfo,
            FulfilledRequirement fulfilledExecutorRequirement,
            Offer launchOffer,
            String taskType) {

        TaskInfo taskInfo = taskReq.getTaskInfo();
        List<Resource> fulfilledTaskResources = fulfilledTaskRequirement.getFulfilledResources();
        TaskInfo.Builder taskBuilder = TaskInfo.newBuilder(taskInfo)
                .clearResources()
                .addAllResources(fulfilledTaskResources);

        if (execInfo.isPresent()) {
            ExecutorInfo.Builder execBuilder =
                    ExecutorInfo.newBuilder(execInfo.get()).clearResources();

            if (fulfilledExecutorRequirement != null) {
                List<Resource> fulfilledExecutorResources = fulfilledExecutorRequirement.getFulfilledResources();
                execBuilder.addAllResources(fulfilledExecutorResources);
                execBuilder = ResourceUtils.updateEnvironment(execBuilder, fulfilledExecutorResources);
            } else {
                execBuilder.addAllResources(execInfo.get().getResourcesList());
                execBuilder = ResourceUtils.updateEnvironment(execBuilder, execInfo.get().getResourcesList());
            }

            taskBuilder.setExecutor(execBuilder.build());
        }

        // Store metadata in the TaskInfo for later access by constraint filters:
        taskBuilder = TaskUtils.setOfferAttributes(taskBuilder, launchOffer);
        taskBuilder = TaskUtils.setTaskType(taskBuilder, taskType);
        taskBuilder = ResourceUtils.serializeCommandInfo(
                ResourceUtils.updateEnvironment(taskBuilder, fulfilledTaskResources));

        return taskBuilder.build();
    }
}
