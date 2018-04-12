package com.mesosphere.sdk.scheduler.multi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.http.endpoints.*;
import com.mesosphere.sdk.http.types.StringPropertyDeserializer;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.OfferUtils;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.scheduler.MesosEventClient;
import com.mesosphere.sdk.scheduler.OfferResources;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.plan.DefaultPhase;
import com.mesosphere.sdk.scheduler.plan.DefaultPlan;
import com.mesosphere.sdk.scheduler.plan.DefaultPlanManager;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.uninstall.DeregisterStep;

/**
 * An implementation of {@link MesosEventClient} which wraps multiple running services, routing Mesos events to each
 * client appropriately. The underlying running services are stored within a provided {@link MultiServiceManager}.
 */
public class MultiServiceEventClient implements MesosEventClient {

    /**
     * Interface for notifying the caller that an added service has completed uninstalling and has been removed.
     */
    public interface UninstallCallback {
        /**
         * Invoked when a given service has completed its uninstall as triggered by
         * {@link MultiServiceEventClient#uninstallService(String)}. After this has been called, re-adding the service
         * to the {@link MultiServiceEventClient} will result in launching a new instance from scratch.
         */
        void uninstalled(String serviceName);
    }

    private static final Logger LOGGER = LoggingUtils.getLogger(MultiServiceEventClient.class);

    private final String frameworkName;
    private final SchedulerConfig schedulerConfig;
    private final MultiServiceManager multiServiceManager;
    private final Collection<Object> customEndpoints;
    private final UninstallCallback uninstallCallback;

    // Additional handling for when we're uninstalling the entire Scheduler.
    private final DeregisterStep deregisterStep;
    private final Optional<Plan> uninstallPlan;

    // Keep track of when services started uninstalling. We remove services that have been stuck in an uninstalling
    // state for too long. This is to e.g. avoid waiting forever on a dead agent, and instead just clean up if it comes
    // back later. Note that we wait indefinitely if the entire scheduler is being uninstalled. Also note that this is
    // best-effort: if the scheduler restarts then these times are reset.
    private final Optional<Long> uninstallTimeout;
    private final Map<String, Long> uninstallStartTimes;

    // Keep track of which services have a RESERVING status. We only pass offers to one RESERVING service at a time.
    private Optional<String> selectedReservingService;
    // A listing of all RESERVING services. This is intentionally an ordered set to ensure that deployment selection is
    // consistent (but still arbitrary). Consistent selection is needed if the scheduler process is restarted.
    // For example, we wouldn't want to deploy a bit of A, then B, then A again. Instead we want to stick to A until
    // it's finished. But the alphabetical solution isn't perfect. A user could add a service that gets alphabetical
    // priority, THEN restart the scheduler, causing the scheduler to pick the new one. But that's a very special case,
    // particularly with how quickly it should take for footprint reservation to complete: TODO(nickbp, INFINITY-3476)
    private final TreeSet<String> allReservingServices;

    public MultiServiceEventClient(
            String frameworkName,
            SchedulerConfig schedulerConfig,
            MultiServiceManager multiServiceManager,
            Collection<Object> customEndpoints,
            UninstallCallback uninstallCallback) {
        this.frameworkName = frameworkName;
        this.schedulerConfig = schedulerConfig;
        this.multiServiceManager = multiServiceManager;
        this.customEndpoints = customEndpoints;
        this.uninstallCallback = uninstallCallback;

        if (schedulerConfig.isUninstallEnabled()) {
            this.deregisterStep = new DeregisterStep(Optional.empty());
            this.uninstallPlan = Optional.of(
                    new DefaultPlan(Constants.DEPLOY_PLAN_NAME, Collections.singletonList(
                            new DefaultPhase(
                                    "deregister-framework",
                                    Collections.singletonList(deregisterStep),
                                    new SerialStrategy<>(),
                                    Collections.emptyList()))));
            // No timeout when whole scheduler is uninstalling, make user see what's up:
            this.uninstallTimeout = Optional.empty();
        } else {
            this.deregisterStep = null;
            this.uninstallPlan = Optional.empty();
            // Use uninstall timeout, unless disabled in SchedulerConfig with negative or zero value
            long timeoutSecs = schedulerConfig.getMultiServiceRemovalTimeout().getSeconds();
            this.uninstallTimeout = timeoutSecs <= 0 ? Optional.empty() : Optional.of(timeoutSecs);
        }
        this.uninstallStartTimes = new HashMap<>();

        this.selectedReservingService = Optional.empty();
        this.allReservingServices = new TreeSet<>();
    }

    @Override
    public void registered(boolean reRegistered) {
        multiServiceManager.registered(reRegistered);
    }

    @Override
    public void unregistered() {
        if (!uninstallPlan.isPresent()) {
            // This should have only happened after we returned OfferResponse.finished() below
            throw new IllegalStateException("unregistered() called, but the we are not uninstalling");
        }
        deregisterStep.setComplete();
    }

    @Override
    public ClientStatusResponse getClientStatus() {
        // If the entire scheduler is uninstalling and there are no clients left to uninstall, then tell upstream that
        // we're uninstalled.
        boolean noClients = false;

        Collection<String> finishedServices = new HashSet<>();
        Collection<AbstractScheduler> uninstalledServices = new HashSet<>();

        Collection<AbstractScheduler> services = multiServiceManager.sharedLockAndGetServices();
        LOGGER.info("Checking status of {} service{}:", services.size(), services.size() == 1 ? "" : "s");
        allReservingServices.clear();
        try {
            if (services.isEmpty()) {
                // If we don't have any clients, then WE aren't ready.
                // Decline short, or finish if there's an uninstall in progress.
                noClients = true;
            }
            for (AbstractScheduler service : services) {
                String serviceName = service.getServiceSpec().getName();
                ClientStatusResponse response = service.getClientStatus();
                LOGGER.info("  {} status result: {}", serviceName, response.result);

                switch (response.result) {
                case RESERVING:
                    // Keep track of all the reserving services. Only one reserving service can get offers at any given
                    // time. This prevents two reserving services from deadlocking each other.
                    // TODO(nickbp, INFINITY-3476): Once the underlying service is just collecting footprint for this
                    //     stage, implement an alert for when footprint collection takes too long. In the meantime, we
                    //     can't make any assumptions about what 'too long' is, due to potential readiness checks etc.
                    allReservingServices.add(serviceName);
                    break;
                case RUNNING:
                    // No-op, leave it as-is.
                    break;
                case FINISHED:
                    // This client has completed running and can be switched to uninstall.
                    finishedServices.add(serviceName);
                    break;
                case UNINSTALLING:
                    // Check uninstall timeout, if enabled.
                    if (uninstallTimeout.isPresent()) {
                        long nowSecs = getCurrentTimeMillis() / 1000;
                        Long uninstallStart = uninstallStartTimes.get(serviceName);
                        if (uninstallStart == null) {
                            // Mark this service as uninstalling
                            uninstallStartTimes.put(serviceName, nowSecs);
                        } else if (nowSecs > uninstallStart + uninstallTimeout.get()) {
                            // Timeout has been exceeded. Force-remove the service.
                            Optional<Plan> deployPlan = service.getPlans().stream()
                                    .filter(plan -> plan.isDeployPlan())
                                    .findAny();
                            LOGGER.error("Service {} has failed to complete uninstall within {}s timeout, "
                                    + "forcing cleanup. All active uninstalls: {}, {} deploy plan: {}",
                                    serviceName,
                                    uninstallTimeout.get(),
                                    uninstallStartTimes,
                                    serviceName,
                                    deployPlan.isPresent() ? deployPlan.get().toString() : "UNKNOWN");
                            uninstalledServices.add(service);
                        }
                    }
                    break;
                case UNINSTALLED:
                    // This client has completed uninstall.
                    // Its data can be removed and its entry can be dropped from the MultiServiceManager (below).
                    uninstalledServices.add(service);
                    break;
                }

                // If we run out of unusedOffers we still keep going with an empty list of offers.
                // This is done in case any of the clients depends on us to turn the crank periodically.
            }
        } finally {
            if (allReservingServices.isEmpty()) {
                // Nothing is reserving. Clear the allowed deployment selection (if any).
                selectedReservingService = Optional.empty();
            } else {
                String clause = "continuing with";
                if (!selectedReservingService.isPresent()
                        || !allReservingServices.contains(selectedReservingService.get())) {
                    // Something is reserving, and the selected service is out of date. Update the selection.
                    selectedReservingService = Optional.of(allReservingServices.iterator().next());
                    clause = "selecting";
                }
                LOGGER.info("{} service{} reserving, {} '{}': {}",
                        allReservingServices.size(),
                        allReservingServices.size() == 1 ? " is" : "s are",
                        clause,
                        selectedReservingService.get(),
                        allReservingServices);
            }
            multiServiceManager.sharedUnlock();
        }

        if (!finishedServices.isEmpty()) {
            LOGGER.info("Starting uninstall for {} service{}: {} (from {} total services)",
                    finishedServices.size(), finishedServices.size() == 1 ? "" : "s", finishedServices);
            // Trigger uninstalls. Grabs a lock internally, so we need to be unlocked when calling it here.
            multiServiceManager.uninstallServices(finishedServices);
        }

        if (!uninstalledServices.isEmpty()) {
            // Note: It's possible that we can have a race where we attempt to remove the same service twice. This is ok
            //       (Picture two near-simultaneous calls to offers(): Both send offers, both get FINISHED back, ...)
            multiServiceManager.removeServices(uninstalledServices.stream()
                    .map(service -> service.getServiceSpec().getName())
                    .collect(Collectors.toSet()));
            noClients = multiServiceManager.getServiceNames().isEmpty();

            for (AbstractScheduler service : uninstalledServices) {
                service.getStateStore().deleteAllDataIfNamespaced();

                String serviceName = service.getServiceSpec().getName();
                // Just in case, avoid invoking the uninstall callback until we are in an unlocked state. This avoids
                // deadlock if the callback itself calls back into us for any reason. This also ensures that we aren't
                // blocking other operations (e.g. offer/status handling) while these callbacks are running.
                uninstallCallback.uninstalled(serviceName);

                // Remove this service from timeout tracking, if enabled.
                uninstallStartTimes.remove(serviceName);
            }
        }

        if (noClients) {
            // There are no active services to process offers. Should the rest of the framework be torn down?
            if (uninstallPlan.isPresent()) {
                // Yes: We're uninstalling everything and all services have been cleaned up. Tell the caller that they
                // can finish with final framework cleanup. After they've finished, they will invoke unregistered(), at
                // which point we can set our deploy plan to complete.
                return ClientStatusResponse.uninstalled();
            } else {
                // No: We're just not actively running anything. Behave normally until we have work to do.
                return ClientStatusResponse.running();
            }
        } else {
            // Clients are still present, behave normally.
            return ClientStatusResponse.running();
        }
    }

    /**
     * Forwards the provided offer(s) to all enclosed services, seeing which services are interested in them.
     *
     * TODO(data-agility): Lots of opportunities to optimize this. Needs benchmarks. For example:
     * <ul>
     * <li>- Hide reserved resources from services that they don't belong to</li>
     * <li>- Forward the offers to a random ordering of services to avoid some services starving others</li>
     * <li>- Distribute the offers across all services in parallel (optimistic offers)</li>
     * <li>- ... Pretty much anything that you could see Mesos itself doing.</li>
     * </ul>
     */
    @Override
    public OfferResponse offers(Collection<Protos.Offer> offers) {
        // Decline short if any client isn't ready.
        boolean anyClientsNotReady = false;

        List<OfferRecommendation> recommendations = new ArrayList<>();
        List<Protos.Offer> remainingOffers = new ArrayList<>();
        remainingOffers.addAll(offers);

        Collection<AbstractScheduler> services = multiServiceManager.sharedLockAndGetServices();
        if (!services.isEmpty()) {
            LOGGER.info("Sending {} offer{} to {} service{}:",
                    offers.size(), offers.size() == 1 ? "" : "s",
                    services.size(), services.size() == 1 ? "" : "s");
        }
        try {
            for (AbstractScheduler service : services) {
                String serviceName = service.getServiceSpec().getName();
                if (selectedReservingService.isPresent()
                        && !serviceName.equals(selectedReservingService.get())
                        && allReservingServices.contains(serviceName)) {
                    // This service is in a reserving state, but it is NOT the selected service.
                    // Avoid providing it with offers until it's been selected.
                    LOGGER.info("  {} isn't the selected deployment ({}): not sending offers",
                            serviceName, selectedReservingService.get());
                    continue;
                }
                OfferResponse response = service.offers(remainingOffers);
                if (!remainingOffers.isEmpty() && !response.recommendations.isEmpty()) {
                    // Some offers were consumed. Update what remains to offer to the next service.
                    List<Protos.Offer> updatedRemainingOffers =
                            OfferUtils.filterOutAcceptedOffers(remainingOffers, response.recommendations);
                    remainingOffers = updatedRemainingOffers;
                }
                recommendations.addAll(response.recommendations);
                LOGGER.info("  {} offer result: {}[{} rec{}], {} offer{} remaining",
                        serviceName,
                        response.result,
                        response.recommendations.size(), response.recommendations.size() == 1 ? "" : "s",
                        remainingOffers.size(), remainingOffers.size() == 1 ? "" : "s");

                switch (response.result) {
                case NOT_READY:
                    // This client wasn't ready. Tell upstream to short-decline any remaining offers so that it can get
                    // another chance shortly.
                    anyClientsNotReady = true;
                    break;
                case PROCESSED:
                    // No-op, keep going.
                    break;
                }

                // If we run out of unusedOffers we still keep going with an empty list of offers.
                // This is done in case any of the clients depends on us to turn the crank periodically.
            }
        } finally {
            multiServiceManager.sharedUnlock();
        }

        if (anyClientsNotReady) {
            // One or more clients said they weren't ready. Tell upstream to short-decline the unused offers, but still
            // perform any operations returned by the ready clients.
            return OfferResponse.notReady(recommendations);
        } else {
            // We have one or more clients and they were all able to process offers, so tell upstream to long-decline.
            return OfferResponse.processed(recommendations);
        }
    }

    /**
     * Maps the reserved resources in the provided unused offers according to the services that own them, then queries
     * those services directly to see what resources they consider unexpected.
     *
     * <p>This is an optimization which avoids querying services about unexpected resources that don't relate to them.
     * <p>In addition to reducing unnecessary queries, this also improves isolation between services. They only see
     * resources which relate to them.
     */
    @Override
    public UnexpectedResourcesResponse getUnexpectedResources(Collection<Protos.Offer> unusedOffers) {
        // Resources can be unexpected for any of the following reasons:
        // CASE 1: Resources with an unrecognized service name (old resources?)
        // CASE 2: Resources whose matching service returned them as unexpected (old/decommissioned resources?)

        // For each offer, the resources which should be unreserved.
        Map<Protos.OfferID, OfferResources> unexpectedResources = new HashMap<>();
        // For each service, the resources associated with that service, paired with their parent offer(s)
        // In other words: serviceName => offerId => offer + [resourcesForService]
        Map<String, Map<Protos.OfferID, OfferResources>> offersByService = new HashMap<>();

        // Map reserved resources (and their parent offers) to the service that they're assigned to.
        // We can then query those services with the subset of offers that belong to them.
        for (Protos.Offer offer : unusedOffers) {
            for (Protos.Resource resource : offer.getResourcesList()) {
                Optional<String> serviceName = ResourceUtils.getNamespace(resource);
                if (serviceName.isPresent()) {
                    // Found service name: Store resource against serviceName+offerId to be evaluated below.
                    getEntry(offersByService, serviceName.get(), offer).add(resource);
                } else if (ResourceUtils.getReservation(resource).isPresent()) {
                    // This reserved resource is malformed. Reservations created by this scheduler should always have a
                    // service name label. Make some noise but leave it alone. Out of caution, we DO NOT destroy it.
                    LOGGER.error("Ignoring malformed resource in offer {} (missing namespace label): {}",
                            offer.getId().getValue(), TextFormat.shortDebugString(resource));
                } else {
                    // Not a reserved resource. Ignore for cleanup purposes.
                }
            }
        }
        if (!offersByService.isEmpty()) {
            LOGGER.info("Sorted reserved resources from {} offer{} into {} services: {}",
                    unusedOffers.size(),
                    unusedOffers.size() == 1 ? "" : "s",
                    offersByService.size(),
                    offersByService.keySet());
        }
        if (!unexpectedResources.isEmpty()) {
            LOGGER.warn("Encountered {} malformed resources to clean up: {}",
                    unexpectedResources.size(), unexpectedResources.values());
        }

        // Iterate over offersByService and find out if the services in question still want the resources.
        // Any unwanted resources then get added to unexpectedResources.
        boolean anyFailedClients = false;
        for (Map.Entry<String, Map<Protos.OfferID, OfferResources>> entry : offersByService.entrySet()) {
            String serviceName = entry.getKey();
            Collection<OfferResources> serviceOffers = entry.getValue().values();

            Optional<AbstractScheduler> service = multiServiceManager.getService(serviceName);
            if (!service.isPresent()) {
                // (CASE 1) Old or invalid service name. Consider all resources for this service as unexpected.
                LOGGER.info("  {} cleanup result: unknown service, all resources unexpected", serviceName);
                for (OfferResources serviceOffer : serviceOffers) {
                    getEntry(unexpectedResources, serviceOffer.getOffer()).addAll(serviceOffer.getResources());
                }
            } else {
                // Construct offers containing (only) these resources and pass them to the service.
                // See which of the resources are now unexpected by the service.
                List<Protos.Offer> offersToSend = new ArrayList<>(serviceOffers.size());
                for (OfferResources serviceOfferResources : serviceOffers) {
                    offersToSend.add(serviceOfferResources.getOffer().toBuilder()
                            .clearResources()
                            .addAllResources(serviceOfferResources.getResources())
                            .build());
                }
                // (CASE 2) The service has returned the subset of these resources which are unexpected.
                // Add those to unexpectedResources.
                // Note: We're careful to only invoke this once per service, as the call is likely to be expensive.
                UnexpectedResourcesResponse response =
                        service.get().getUnexpectedResources(offersToSend);
                LOGGER.info("  {} cleanup result: {} with {} unexpected resources in {} offer{}",
                        serviceName,
                        response.result,
                        response.offerResources.stream()
                                .collect(Collectors.summingInt(or -> or.getResources().size())),
                        response.offerResources.size(),
                        response.offerResources.size() == 1 ? "" : "s");
                switch (response.result) {
                case FAILED:
                    // We should be able to safely proceed to the next service rather than aborting here.
                    // Play it safe by telling upstream to do a short decline.
                    anyFailedClients = true;
                    for (OfferResources unexpectedInOffer : response.offerResources) {
                        getEntry(unexpectedResources, unexpectedInOffer.getOffer())
                                .addAll(unexpectedInOffer.getResources());
                    }
                    break;
                case PROCESSED:
                    for (OfferResources unexpectedInOffer : response.offerResources) {
                        getEntry(unexpectedResources, unexpectedInOffer.getOffer())
                                .addAll(unexpectedInOffer.getResources());
                    }
                    break;
                }
            }
        }

        // Return the combined listing of unexpected resources across all services:
        return anyFailedClients
                ? UnexpectedResourcesResponse.failed(unexpectedResources.values())
                : UnexpectedResourcesResponse.processed(unexpectedResources.values());
    }

    /**
     * Maps the provided status to the service that owns its task, then queries that service with the status.
     *
     * <p>This is an optimization which avoids querying services about task statuses that don't relate to them.
     * <p>In addition to reducing unnecessary queries, this also improves isolation between services. They only see
     * task statuses which relate to them.
     */
    @Override
    public TaskStatusResponse taskStatus(Protos.TaskStatus status) {
        Optional<AbstractScheduler> service = multiServiceManager.getMatchingService(status);
        if (!service.isPresent()) {
            // Unrecognized service. Status for old task?
            LOGGER.info("Received status for unknown task {}: {}",
                    status.getTaskId().getValue(), TextFormat.shortDebugString(status));
            return TaskStatusResponse.unknownTask();
        }
        LOGGER.info("Received status for task {}: {}", status.getTaskId().getValue(), status.getState());
        return service.get().taskStatus(status);
    }

    /**
     * Returns a set of multi-service-specific endpoints to be served by the scheduler. This effectively overrides the
     * underlying per-service endpoints with multiservice-aware versions.
     */
    @Override
    public Collection<Object> getHTTPEndpoints() {
        Collection<PlanManager> planManagers = uninstallPlan.isPresent()
                ? Collections.singletonList(DefaultPlanManager.createProceeding(uninstallPlan.get()))
                : Collections.emptyList(); // ... any plans to show when running normally?
        List<Object> endpoints = new ArrayList<>();
        endpoints.addAll(Arrays.asList(
                new HealthResource(planManagers),
                new PlansResource(planManagers),
                new MultiArtifactResource(multiServiceManager),
                new MultiConfigResource(multiServiceManager),
                new MultiEndpointsResource(frameworkName, multiServiceManager, schedulerConfig),
                new MultiPlansResource(multiServiceManager),
                new MultiPodResource(multiServiceManager),
                new MultiStateResource(multiServiceManager, new StringPropertyDeserializer())));
        endpoints.addAll(customEndpoints);
        return endpoints;
    }

    /**
     * Time retrieval broken out into a separate function to allow overriding its behavior in tests.
     */
    @VisibleForTesting
    protected long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Finds the requested {@link OfferResources} value in the provided map[serviceName][offerId], initializing the
     * entry if needed.
     */
    private static OfferResources getEntry(
            Map<String, Map<Protos.OfferID, OfferResources>> map, String serviceName, Protos.Offer offer) {
        Map<Protos.OfferID, OfferResources> serviceOffers = map.get(serviceName);
        if (serviceOffers == null) {
            serviceOffers = new HashMap<>();
            map.put(serviceName, serviceOffers);
        }
        return getEntry(serviceOffers, offer);
    }

    /**
     * Finds the requested {@link OfferResources} value in the provided map[offerId], initializing the entry if needed.
     */
    private static OfferResources getEntry(Map<Protos.OfferID, OfferResources> map, Protos.Offer offer) {
        OfferResources currentValue = map.get(offer.getId());
        if (currentValue == null) {
            // Initialize entry
            currentValue = new OfferResources(offer);
            map.put(offer.getId(), currentValue);
        }
        return currentValue;
    }
}
