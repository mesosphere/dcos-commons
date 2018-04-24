package com.mesosphere.sdk.scheduler.multi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

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
 * service appropriately. The underlying running services are stored within a provided {@link MultiServiceManager}.
 */
public class MultiServiceEventClient implements MesosEventClient {

    /**
     * Interface for notifying the caller that an added service has completed uninstalling and has been removed.
     */
    public interface UninstallCallback {
        /**
         * Invoked when a given service has completed its uninstall as triggered by {@link #uninstallService(String)}.
         * After this has been called, re-adding the service to the {@link MultiServiceEventClient} will result in
         * launching a new instance from scratch.
         */
        void uninstalled(String serviceName);
    }

    private static final Logger LOGGER = LoggingUtils.getLogger(MultiServiceEventClient.class);

    private final String frameworkName;
    private final SchedulerConfig schedulerConfig;
    private final MultiServiceManager multiServiceManager;
    private final OfferDiscipline offerDiscipline;
    private final Collection<Object> customEndpoints;
    private final UninstallCallback uninstallCallback;

    // Additional handling for when we're uninstalling the entire Scheduler.
    private final DeregisterStep deregisterStep;
    private final Optional<Plan> uninstallPlan;

    public MultiServiceEventClient(
            String frameworkName,
            SchedulerConfig schedulerConfig,
            MultiServiceManager multiServiceManager,
            OfferDiscipline offerDiscipline,
            Collection<Object> customEndpoints,
            UninstallCallback uninstallCallback) {
        this.frameworkName = frameworkName;
        this.schedulerConfig = schedulerConfig;
        this.multiServiceManager = multiServiceManager;
        this.offerDiscipline = offerDiscipline;
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
        } else {
            this.deregisterStep = null;
            this.uninstallPlan = Optional.empty();
        }
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

    /**
     * Returns our status to the upstream {@code OfferProcessor}. In practice, we only return either {@code RUNNING}, or
     * {@code UNINSTALLED} if we're ready to be torn down during a scheduler uninstall.
     */
    @Override
    public ClientStatusResponse getClientStatus() {
        // If the entire scheduler is uninstalling and there are no services left to uninstall, then tell upstream that
        // we're uninstalled.
        boolean noServices = false;

        Collection<AbstractScheduler> services = multiServiceManager.sharedLockAndGetServices();
        try {
            noServices = services.isEmpty();
        } finally {
            multiServiceManager.sharedUnlock();
        }

        if (noServices) {
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
            // Services are still present, behave normally.
            return ClientStatusResponse.running();
        }
    }

    /**
     * Forwards the provided offer(s) to all enclosed services, seeing which services are interested in them. The
     * services which actually receive offers is decided by the {@link OfferDiscipline}.
     */
    @Override
    public OfferResponse offers(Collection<Protos.Offer> offers) {
        Collection<AbstractScheduler> servicesToGiveOffers = new ArrayList<>();
        Collection<String> servicesToUninstall = new ArrayList<>();
        Collection<AbstractScheduler> servicesToRemove = new ArrayList<>();

        Collection<AbstractScheduler> services = multiServiceManager.sharedLockAndGetServices();
        try {
            if (services.isEmpty()) {
                // Nothing to do, short-circuit.
                return OfferResponse.processed(Collections.emptyList());
            }

            // Update the offer discipline with the current list of services.
            try {
                offerDiscipline.updateServices(services);
            } catch (Exception e) {
                LOGGER.error("Failed to update selected services in offer discipline, trying again later", e);
                return OfferResponse.notReady(Collections.emptyList());
            }

            // Before we handle offers, check for any finished services that should be switched to uninstall, or any
            // uninstalled services that should be removed.
            LOGGER.info("Sending {} offer{} to {} service{}:",
                    offers.size(), offers.size() == 1 ? "" : "s",
                    services.size(), services.size() == 1 ? "" : "s");
            for (AbstractScheduler service : services) {
                String serviceName = service.getServiceSpec().getName();
                ClientStatusResponse statusResponse = service.getClientStatus();
                if (statusResponse.result != ClientStatusResponse.Result.RUNNING) {
                    // Only log status when it's special/unusual
                    LOGGER.info("{} status: {}", serviceName, statusResponse.result);
                }

                // Note: We ALWAYS invoke the offer discipline regardless of status response. This allows the offer
                // discipline to add/remove selected services based on that status.
                boolean sendOffers = offerDiscipline.offersEnabled(statusResponse, service);

                switch (statusResponse.result) {
                case RESERVING:
                    //$FALL-THROUGH$
                case RUNNING:
                    if (sendOffers) {
                        servicesToGiveOffers.add(service);
                    }
                    break;
                case FINISHED:
                    // This service has completed running and can be switched to uninstall.
                    servicesToUninstall.add(serviceName);
                    break;
                case UNINSTALLED:
                    // This service has completed uninstall and can be torn down.
                    servicesToRemove.add(service);
                    break;
                }

                // If we run out of unusedOffers we still keep going with an empty list of offers.
                // This is done in case any of the services depends on us to turn the crank periodically.
            }
        } finally {
            multiServiceManager.sharedUnlock();
        }

        // Decline short if any service isn't ready.
        boolean anyServicesNotReady = false;
        List<OfferRecommendation> recommendations = new ArrayList<>();
        if (!servicesToGiveOffers.isEmpty()) {
            LOGGER.info("Sending offers to {} service{}:",
                    servicesToGiveOffers.size(), servicesToGiveOffers.size() == 1 ? "" : "s");
            List<Protos.Offer> remainingOffers = new ArrayList<>();
            remainingOffers.addAll(offers);
            for (AbstractScheduler service : servicesToGiveOffers) {
                OfferResponse offerResponse = service.offers(remainingOffers);
                recommendations.addAll(offerResponse.recommendations);
                if (!remainingOffers.isEmpty() && !offerResponse.recommendations.isEmpty()) {
                    // Some offers were consumed. Update what remains to offer to the next service.
                    remainingOffers =
                            OfferUtils.filterOutAcceptedOffers(remainingOffers, offerResponse.recommendations);
                }
                boolean readyForOffers = offerResponse.result == OfferResponse.Result.PROCESSED;
                if (!offerResponse.recommendations.isEmpty() || !readyForOffers) {
                    // Only log result when it's non-empty/unusual
                    LOGGER.info("{} offer result: {}[{} recommendation{}], {} offer{} remaining",
                            service.getServiceSpec().getName(),
                            offerResponse.result,
                            offerResponse.recommendations.size(),
                            offerResponse.recommendations.size() == 1 ? "" : "s",
                            remainingOffers.size(),
                            remainingOffers.size() == 1 ? "" : "s");
                }
                if (!readyForOffers) {
                    anyServicesNotReady = true;
                }
            }
        }

        if (!servicesToUninstall.isEmpty()) {
            LOGGER.info("Starting uninstall for {} service{}: {}",
                    servicesToUninstall.size(), servicesToUninstall.size() == 1 ? "" : "s", servicesToUninstall);
            // Trigger uninstalls. Grabs a lock internally, so we need to be unlocked when calling it here.
            multiServiceManager.uninstallServices(servicesToUninstall);
        }

        if (!servicesToRemove.isEmpty()) {
            LOGGER.info("Removing {} uninstalled service{}: {}",
                    servicesToRemove.size(), servicesToRemove.size() == 1 ? "" : "s", servicesToRemove);

            // Note: It's possible that we can have a race where we attempt to remove a service twice. This is fine.
            //       (Picture two near-simultaneous calls to offers(): Both send offers, both get FINISHED back, ...)
            multiServiceManager.removeServices(servicesToRemove.stream()
                    .map(service -> service.getServiceSpec().getName())
                    .collect(Collectors.toList()));

            for (AbstractScheduler service : servicesToRemove) {
                service.getStateStore().deleteAllDataIfNamespaced();

                // Just in case, avoid invoking the uninstall callback until we are in an unlocked state. This avoids
                // deadlock if the callback itself calls back into us for any reason. This also ensures that we aren't
                // blocking other operations (e.g. offer/status handling) while these callbacks are running.
                uninstallCallback.uninstalled(service.getServiceSpec().getName());
            }
        }

        if (anyServicesNotReady) {
            // One or more services said they weren't ready. Tell upstream to short-decline the unused offers, but still
            // perform any operations returned by the ready services.
            return OfferResponse.notReady(recommendations);
        } else {
            // We have one or more services and they were all able to process offers, so tell upstream to long-decline.
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
        boolean anyFailedServices = false;
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
                UnexpectedResourcesResponse response = service.get().getUnexpectedResources(offersToSend);
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
                    anyFailedServices = true;
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
        return anyFailedServices
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
