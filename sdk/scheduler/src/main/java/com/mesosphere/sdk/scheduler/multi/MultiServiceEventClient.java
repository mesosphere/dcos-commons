package com.mesosphere.sdk.scheduler.multi;

import com.mesosphere.sdk.http.endpoints.HealthResource;
import com.mesosphere.sdk.http.endpoints.MultiArtifactResource;
import com.mesosphere.sdk.http.endpoints.MultiConfigResource;
import com.mesosphere.sdk.http.endpoints.MultiEndpointsResource;
import com.mesosphere.sdk.http.endpoints.MultiPlansResource;
import com.mesosphere.sdk.http.endpoints.MultiPodResource;
import com.mesosphere.sdk.http.endpoints.MultiStateResource;
import com.mesosphere.sdk.http.endpoints.PlansResource;
import com.mesosphere.sdk.http.types.StringPropertyDeserializer;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.OfferUtils;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.MesosEventClient;
import com.mesosphere.sdk.scheduler.OfferResources;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.DefaultPhase;
import com.mesosphere.sdk.scheduler.plan.DefaultPlan;
import com.mesosphere.sdk.scheduler.plan.DefaultPlanManager;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.uninstall.DeregisterStep;
import com.mesosphere.sdk.storage.Persister;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An implementation of {@link MesosEventClient} which wraps multiple running services, routing Mesos events to each
 * service appropriately. The underlying running services are stored within a provided {@link MultiServiceManager}.
 */
public class MultiServiceEventClient implements MesosEventClient {

  private static final Logger LOGGER = LoggingUtils.getLogger(MultiServiceEventClient.class);

  private final String frameworkName;

  private final SchedulerConfig schedulerConfig;

  private final MultiServiceManager multiServiceManager;

  private final Collection<Object> customEndpoints;

  private final UninstallCallback uninstallCallback;

  private final OfferDiscipline offerDiscipline;

  // Additional handling for when we're uninstalling the entire Scheduler.
  private final Optional<DeregisterStep> deregisterStep;

  // Calculated during a call to offers(), to be returned in the following call to getClientStatus().
  private final Collection<String> serviceNamesToGiveOffers;

  public MultiServiceEventClient(
      String frameworkName,
      SchedulerConfig schedulerConfig,
      MultiServiceManager multiServiceManager,
      Persister persister,
      Collection<Object> customEndpoints,
      UninstallCallback uninstallCallback)
  {
    this(
        frameworkName,
        schedulerConfig,
        multiServiceManager,
        (schedulerConfig.getMultiServiceReserveDiscipline() > 0)
            ? new ParallelFootprintDiscipline(
            schedulerConfig.getMultiServiceReserveDiscipline(),
            new DisciplineSelectionStore(persister))
            : new AllDiscipline(),
        customEndpoints,
        uninstallCallback,
        schedulerConfig.isUninstallEnabled()
            ? Optional.of(new DeregisterStep(Optional.empty()))
            : Optional.empty());
  }

  @VisibleForTesting
  MultiServiceEventClient(
      String frameworkName,
      SchedulerConfig schedulerConfig,
      MultiServiceManager multiServiceManager,
      OfferDiscipline offerDiscipline,
      Collection<Object> customEndpoints,
      UninstallCallback uninstallCallback,
      Optional<DeregisterStep> deregisterStep)
  {
    this.frameworkName = frameworkName;
    this.schedulerConfig = schedulerConfig;
    this.multiServiceManager = multiServiceManager;
    this.customEndpoints = customEndpoints;
    this.uninstallCallback = uninstallCallback;
    this.offerDiscipline = offerDiscipline;
    this.deregisterStep = deregisterStep;
    this.serviceNamesToGiveOffers = new ArrayList<>();
  }

  /**
   * Finds the requested {@link OfferResources} value in the provided map[serviceName][offerId], initializing the
   * entry if needed.
   */
  private static OfferResources getEntry(
      Map<String, Map<Protos.OfferID, OfferResources>> map,
      String serviceName,
      Protos.Offer offer
  )
  {
    return getEntry(map.computeIfAbsent(serviceName, k -> new HashMap<>()), offer);
  }

  /**
   * Finds the requested {@link OfferResources} value in the provided map[offerId], initializing the entry if needed.
   */
  private static OfferResources getEntry(
      Map<Protos.OfferID, OfferResources> map,
      Protos.Offer offer)
  {
    // Initialize entry if absent
    return map.computeIfAbsent(offer.getId(), k -> new OfferResources(offer));
  }

  @Override
  public void registered(boolean reRegistered) {
    multiServiceManager.registered(reRegistered);
  }

  @Override
  public void unregistered() {
    if (!deregisterStep.isPresent()) {
      // This should have only happened after we returned OfferResponse.finished() below
      throw new IllegalStateException("unregistered() called, but the we are not uninstalling");
    }
    deregisterStep.get().setComplete();
  }

  /**
   * Returns our status to the upstream {@code OfferProcessor}. The logic is as follows:
   * <p>
   * <ol>
   * <li>If no services are present: uninstall mode: {@code IDLE/REMOVE_CLIENT}, otherwise: {@code IDLE/NONE}</li>
   * <li>If all services are idle: {@code IDLE/NONE}</li>
   * <li>If any service is collecting footprint: {@code WORKING/FOOTPRINT}, with {@code newWork=true} if any service
   * had {@code newWork=true}</li>
   * <li>Else (mix of zero or more {@code IDLE/*} and zero or more {@code WORKING/*}):
   * {@code WORKING/LAUNCH}, with {@code newWork=true} if any service had {@code newWork=true}</li>
   * </ol>
   */
  @Override
  @SuppressWarnings({
      "checkstyle:CyclomaticComplexity",
      "checkstyle:MissingSwitchDefault",
  })
  public ClientStatusResponse getClientStatus() {
    serviceNamesToGiveOffers.clear();
    Collection<String> servicesToUninstall = new ArrayList<>();
    Collection<AbstractScheduler> servicesToRemove = new ArrayList<>();

    final ClientStatusResponse clientStatusToReturn;
    Collection<AbstractScheduler> services = multiServiceManager.sharedLockAndGetServices();
    try {
      if (services.isEmpty()) {
        // There are no active services to process offers. Should the rest of the framework be torn down?
        if (deregisterStep.isPresent()) {
          // Yes: We're uninstalling everything and all services have been cleaned up. Tell the caller that
          // they can finish with final framework cleanup. After they've finished, they will invoke our
          // unregistered() call, at which point we can set the deregisterStep (and therefore our deploy plan)
          // to complete.
          return ClientStatusResponse.readyToRemove();
        } else {
          // No: We're just not actively running anything. Idle until we have work to do.
          return ClientStatusResponse.idle();
        }
      }

      // Update the offer discipline with the current list of services, so that any removed services can be
      // updated.
      try {
        offerDiscipline.updateServices(
            services
                .stream()
                .map(s -> s.getServiceSpec().getName())
                .collect(Collectors.toSet())
        );
      } catch (Exception e) { // SUPPRESS CHECKSTYLE IllegalCatch
        // The offer discipline failed to flush state to ZK. Its in-memory state should be fine though, so just
        // continue as-is for now.
        LOGGER.error(
            "Failed to update selected services in offer discipline, continuing anyway", e
        );
      }

      // Check for any finished services that should be switched to uninstall, or any uninstalled services that
      // should be removed.
      boolean allServicesIdle = true;
      boolean anyServicesFootprint = false;
      boolean anyServicesHaveNewWork = false;
      for (AbstractScheduler service : services) {
        String serviceName = service.getServiceSpec().getName();
        ClientStatusResponse statusResponse = service.getClientStatus();
        if (!statusResponse.equals(ClientStatusResponse.idle())) {
          // Only log status when it's active
          LOGGER.info("{} status: {}", serviceName, statusResponse);
        }

        // Update the offer discipline with the status response we got, and use it's response to decide whether
        // this service should be allowed offers. Note: We ALWAYS invoke this regardless of the status of the
        // service. This allows the offer discipline to update internal state based on that status.
        boolean offersAllowedByDiscipline =
            offerDiscipline.updateServiceStatus(serviceName, statusResponse);

        switch (statusResponse.result) {
          case WORKING:
            allServicesIdle = false;
            if (offersAllowedByDiscipline) {
              serviceNamesToGiveOffers.add(serviceName);
            }
            if (statusResponse.workingStatus.state ==
                ClientStatusResponse.WorkingStatus.State.FOOTPRINT)
            {
              anyServicesFootprint = true;
            }
            if (statusResponse.workingStatus.hasNewWork) {
              anyServicesHaveNewWork = true;
            }
            break;
          case IDLE:
            switch (statusResponse.idleRequest) {
              case NONE:
                // Nothing to do: Don't send offers, don't mark as having new work.
                break;
              case REMOVE_CLIENT:
                // This service has completed uninstall and can be torn down.
                servicesToRemove.add(service);
                break;
              case START_UNINSTALL:
                // This service has completed running and can be switched to uninstall.
                servicesToUninstall.add(serviceName);
                break;
            }
            break;
        }
      }

      if (allServicesIdle) {
        // ALL services are idle, so we can tell upstream that we're idle overall.
        clientStatusToReturn = ClientStatusResponse.idle();
      } else if (anyServicesFootprint) {
        // One or more of the services is getting footprint, so tell upstream that we're getting footprint.
        clientStatusToReturn = ClientStatusResponse.footprint(anyServicesHaveNewWork);
      } else {
        // Otherwise, one or more services isn't idle, and none of them are getting footprint.
        clientStatusToReturn = ClientStatusResponse.launching(anyServicesHaveNewWork);
      }
    } finally {
      multiServiceManager.sharedUnlock();
    }

    if (!servicesToUninstall.isEmpty()) {
      LOGGER.info(
          "Starting uninstall for {} service{}: {}",
          servicesToUninstall.size(),
          servicesToUninstall.size() == 1 ? "" : "s", servicesToUninstall
      );
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

    return clientStatusToReturn;
  }

  /**
   * Forwards the provided offer(s) to all enclosed services, seeing which services are interested in them. The
   * services which actually receive offers is decided by their status response to {@link #getClientStatus()}, as well
   * as the configured {@link OfferDiscipline}.
   */
  @Override
  public OfferResponse offers(Collection<Protos.Offer> offers) {
    if (serviceNamesToGiveOffers.isEmpty()) {
      return OfferResponse.processed(Collections.emptyList());
    }

    LOGGER.info("Sending {} offer{} to {} service{}:",
        offers.size(), offers.size() == 1 ? "" : "s",
        serviceNamesToGiveOffers.size(), serviceNamesToGiveOffers.size() == 1 ? "" : "s");

    // Decline short if any service isn't ready.
    boolean anyServicesNotReady = false;
    List<OfferRecommendation> recommendations = new ArrayList<>();

    List<Protos.Offer> remainingOffers = new ArrayList<>(offers);
    for (String serviceName : serviceNamesToGiveOffers) {
      // Note: If we run out of remainingOffers we regardless keep going with an empty list of offers against all
      // eligible services. We do this to turn the crank on the services periodically.
      Optional<AbstractScheduler> service = multiServiceManager.getService(serviceName);
      if (!service.isPresent()) {
        // In practice this shouldn't happen, unless perhaps the developer removed the service directly.
        LOGGER.warn(
            "Service '{}' was scheduled to receive offers, then later removed: continuing without" +
                " it",
            serviceName
        );
        continue;
      }
      OfferResponse offerResponse = service.get().offers(remainingOffers);
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
            serviceName,
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
   * <p>
   * <p>This is an optimization which avoids querying services about unexpected resources that don't relate to them.
   * <p>In addition to reducing unnecessary queries, this also improves isolation between services. They only see
   * resources which relate to them.
   */
  @Override
  @SuppressWarnings({
      "checkstyle:CyclomaticComplexity",
      "checkstyle:MissingSwitchDefault",
  })
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
          // The resource has reservation information attached, but it lacks the namespace. See if we can send
          // it to a default service, whose name matches the frameworkName, else drop the offer.
          if (multiServiceManager.getServiceSanitized(frameworkName).isPresent()) {
            LOGGER.info("Forwarding resource to default service: {}", frameworkName);
            getEntry(offersByService, frameworkName, offer).add(resource);
          } else {
            // This reserved resource is malformed. Reservations created by this scheduler should always
            // have a service name label. Make some noise but leave it alone. Out of caution, we DO NOT
            // destroy it.
            LOGGER.error(
                "Ignoring malformed resource in offer {} as neither namespace label nor default" +
                    " service is found): {}",
                offer.getId().getValue(), TextFormat.shortDebugString(resource));
          }
        } else {
          // Common case, not a reserved resource. Ignore for cleanup purposes.
          LOGGER.debug("Unable to map the unused offer to any service.");
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

    // Iterate over offersByService and find out if the services in question still want the resources.
    // Any unwanted resources then get added to unexpectedResources.
    boolean anyFailedServices = false;
    for (Map.Entry<String, Map<Protos.OfferID, OfferResources>> entry :
        offersByService.entrySet())
    {
      String serviceName = entry.getKey();
      Collection<OfferResources> serviceOffers = entry.getValue().values();

      Optional<AbstractScheduler> service = multiServiceManager.getService(serviceName);
      if (!service.isPresent()) {
        // (CASE 1) Old or invalid service name. Consider all resources for this service as unexpected.
        LOGGER.info("  {} cleanup result: unknown service, all resources unexpected", serviceName);
        for (OfferResources serviceOffer : serviceOffers) {
          getEntry(
              unexpectedResources,
              serviceOffer.getOffer()
          ).addAll(serviceOffer.getResources());
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
            response.offerResources.stream().mapToInt(or -> or.getResources().size()).sum(),
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
   * <p>
   * <p>This is an optimization which avoids querying services about task statuses that don't relate to them.
   * <p>In addition to reducing unnecessary queries, this also improves isolation between services. They only see
   * task statuses which relate to them.
   */
  @Override
  public TaskStatusResponse taskStatus(Protos.TaskStatus status) {
    return multiServiceManager
        .getMatchingService(status)
        .map(x -> x.taskStatus(status))
        .orElseGet(() -> multiServiceManager
            .getServiceSanitized(frameworkName)
            .map(x -> {
              LOGGER.info("Forwarding task status to default service: {}", frameworkName);
              return x.taskStatus(status);
            })
            .orElseGet(() -> {
              // Unrecognized service. Status for old task ?
              LOGGER.info("Received status for unknown task {}: {}",
                  status.getTaskId().getValue(), TextFormat.shortDebugString(status));
              return TaskStatusResponse.unknownTask();
            })
        );
  }

  /**
   * Returns a set of multi-service-specific endpoints to be served by the scheduler. This effectively overrides the
   * underlying per-service endpoints with multiservice-aware versions.
   */
  @Override
  public Collection<Object> getHTTPEndpoints() {
    Collection<PlanManager> planManagers = deregisterStep.isPresent()
        ? Collections.singletonList(DefaultPlanManager.createProceeding(new DefaultPlan(
        Constants.DEPLOY_PLAN_NAME,
        Collections.singletonList(new DefaultPhase(
            "deregister-framework",
            Collections.singletonList(deregisterStep.get()),
            new SerialStrategy<>(),
            Collections.emptyList())))))
        : Collections.emptyList();
    List<Object> endpoints = new ArrayList<>();
    endpoints.addAll(Arrays.asList(
        new HealthResource(planManagers, schedulerConfig),
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
   * Interface for notifying the caller that an added service has completed uninstalling and has been removed.
   */
  public interface UninstallCallback {
    /**
     * Invoked when a given service has completed its uninstall as triggered by
     * {@link MultiServiceManager#uninstallService(String)}.
     * After this has been called, re-adding the service to the {@link MultiServiceEventClient} will result in
     * launching a new instance from scratch.
     */
    void uninstalled(String serviceName);
  }
}
