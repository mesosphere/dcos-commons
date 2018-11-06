package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.http.endpoints.DeprecatedPlanResource;
import com.mesosphere.sdk.http.endpoints.HealthResource;
import com.mesosphere.sdk.http.endpoints.PlansResource;
import com.mesosphere.sdk.http.types.EndpointProducer;
import com.mesosphere.sdk.http.types.PlanInfo;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.OfferResources;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.DefaultPlanManager;
import com.mesosphere.sdk.scheduler.plan.Element;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PlanCustomizer;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;

import com.google.common.annotations.VisibleForTesting;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This scheduler uninstalls a service and releases all of its resources.
 */
public class UninstallScheduler extends AbstractScheduler {

  private final Logger logger;

  private final ConfigStore<ServiceSpec> configStore;

  private final UninstallRecorder recorder;

  // This step is used in the deploy plan to represent the unregister operation that's handled in FrameworkRunner.
  // We want to ensure that the deploy plan is only marked complete after deregistration has been completed.
  private final DeregisterStep deregisterStubStep;

  private final PlanManager uninstallPlanManager;

  // If an uninstall timeout is enabled, keep track of when the service should have finished uninstalling. This is
  // only enabled in multi-service cases where the service is being removed from an otherwise active scheduler. It is
  // not enabled if the scheduler itself is being uninstalled, to avoid leaking resources without user intervention.
  private final TimeFetcher timeFetcher;

  private final Optional<Long> uninstallDeadlineMillis;

  private final long uninstallTimeoutSecs;

  private final SchedulerConfig schedulerConfig;

  /**
   * Creates a new {@link UninstallScheduler} using the provided components. The {@link UninstallScheduler} builds an
   * uninstall {@link Plan} which will clean up the service's reservations, TLS artifacts, zookeeper data, and any
   * other artifacts from running the service.
   */
  public UninstallScheduler(
      ServiceSpec serviceSpec,
      StateStore stateStore,
      ConfigStore<ServiceSpec> configStore,
      SchedulerConfig schedulerConfig,
      Optional<PlanCustomizer> planCustomizer,
      Optional<String> namespace)
  {
    this(
        serviceSpec,
        stateStore,
        configStore,
        schedulerConfig,
        planCustomizer,
        namespace,
        Optional.empty(),
        new TimeFetcher());
  }

  @VisibleForTesting
  protected UninstallScheduler(
      ServiceSpec serviceSpec,
      StateStore stateStore,
      ConfigStore<ServiceSpec> configStore,
      SchedulerConfig schedulerConfig,
      Optional<PlanCustomizer> planCustomizer,
      Optional<String> namespace,
      Optional<SecretsClient> customSecretsClientForTests,
      TimeFetcher timeFetcher)
  {
    super(serviceSpec, schedulerConfig, stateStore, null, planCustomizer, namespace);
    this.logger = LoggingUtils.getLogger(getClass(), namespace);
    this.configStore = configStore;
    this.schedulerConfig = schedulerConfig;

    if (!StateStoreUtils.isUninstalling(stateStore)) {
      logger.info(
          "Service has been told to uninstall. Marking this in the persistent state store. " +
          "Uninstall cannot be canceled once triggered."
      );
      StateStoreUtils.setUninstalling(stateStore);
    }

    // Construct a plan for uninstalling any remaining resources
    UninstallPlanFactory planFactory = new UninstallPlanFactory(
        serviceSpec, stateStore, schedulerConfig, namespace, customSecretsClientForTests);
    this.recorder = new UninstallRecorder(stateStore, planFactory.getResourceCleanupSteps());
    this.deregisterStubStep = planFactory.getDeregisterStep();

    this.uninstallPlanManager = DefaultPlanManager.createProceeding(planFactory.getPlan());
    try {
      logger.info("Uninstall plan set to: {}",
          SerializationUtils.toJsonString(PlanInfo.forPlan(planFactory.getPlan())));
    } catch (IOException e) {
      logger.error("Failed to deserialize uninstall plan.");
    }

    this.timeFetcher = timeFetcher;
    if (schedulerConfig.isUninstallEnabled()) {
      // No timeout when whole scheduler is uninstalling, make user see what's up:
      this.uninstallTimeoutSecs = -1;
      this.uninstallDeadlineMillis = Optional.empty();
    } else {
      // Use uninstall timeout, unless disabled in SchedulerConfig with negative or zero value
      this.uninstallTimeoutSecs = schedulerConfig.getMultiServiceRemovalTimeout().getSeconds();
      this.uninstallDeadlineMillis = uninstallTimeoutSecs <= 0 ?
          Optional.empty() :
          Optional.of(timeFetcher.getCurrentTimeMillis() +
              TimeUnit.MILLISECONDS.convert(uninstallTimeoutSecs, TimeUnit.SECONDS)
          );
    }

    customizePlans();
  }

  @Override
  public Collection<Object> getHTTPEndpoints() {
    PlansResource plansResource =
        new PlansResource(Collections.singletonList(uninstallPlanManager));
    return Arrays.asList(
        plansResource,
        new DeprecatedPlanResource(plansResource),
        new HealthResource(Collections.singletonList(uninstallPlanManager), schedulerConfig));
  }

  @Override
  public PlanCoordinator getPlanCoordinator() {
    // Return a stub coordinator which only does work against the sole plan manager.
    return new PlanCoordinator() {
      @Override
      public List<Step> getCandidates() {
        return new ArrayList<>(uninstallPlanManager.getCandidates(Collections.emptyList()));
      }

      @Override
      public Collection<PlanManager> getPlanManagers() {
        return Collections.singletonList(uninstallPlanManager);
      }
    };
  }

  @Override
  public Map<String, EndpointProducer> getCustomEndpoints() {
    return Collections.emptyMap();
  }

  @Override
  public ConfigStore<ServiceSpec> getConfigStore() {
    return configStore;
  }

  @Override
  protected void registeredWithMesos() {
    logger.info("Uninstall scheduler registered with Mesos");
  }

  @Override
  public void unregistered() {
    // Mark the the last step of the uninstall plan as complete.
    // Cosmos will then see that the plan is complete and remove us from Marathon.
    deregisterStubStep.setComplete();
  }

  @Override
  protected ClientStatusResponse getStatus() {
    if (deregisterStubStep.isRunning() || deregisterStubStep.isComplete()) {
      // The service resources have been deleted and all that's left is the final deregister operation. After we
      // return uninstalled(), upstream will finish the uninstall by doing one of the following:
      // - Single-service: Upstream will stop/remove the framework, then unregistered() will be called.
      // - Multi-service: Upstream will remove us from the list of services without calling unregistered().
      return ClientStatusResponse.readyToRemove();
    } else if (uninstallDeadlineMillis.isPresent()
        && timeFetcher.getCurrentTimeMillis() > uninstallDeadlineMillis.get())
    {
      // Configured uninstall timeout has passed, and we're still uninstalling. Tell upstream that we're "done".
      Optional<Plan> deployPlan = getPlans()
          .stream()
          .filter(Plan::isDeployPlan)
          .findAny();
      logger.error(
          "Failed to complete uninstall within {}s timeout, forcing cleanup. Deploy plan was: {}",
          uninstallTimeoutSecs,
          deployPlan.isPresent() ? deployPlan.get().toString() : "UNKNOWN"
      );
      return ClientStatusResponse.readyToRemove();
    } else {
      // Still uninstalling, and no timeout has passed.
      // Note: We return launching() instead of footprint() because we aren't growing footprint.
      return ClientStatusResponse.launching(workSetTracker.hasNewWork());
    }
  }

    @Override
    protected void processStatusUpdate(Protos.TaskStatus status) throws Exception {
        stateStore.storeStatus(StateStoreUtils.fetchTaskInfo(stateStore, status).getName(), status);
    }

    // No recommendations. Upstream should invoke the cleaner against any unexpected resources in unclaimed
    // offers (including the ones that apply to our service), and then notify us via clean() so that we can
    // record the ones that apply to us.
    return OfferResponse.processed(Collections.emptyList());
  }

  /**
   * Returns the resources which are not expected by this service. When uninstalling, all resources are unexpected.
   * The {@link UninstallScheduler} just keeps track of them on its 'checklist' as they are removed.
   */
  @Override
  public UnexpectedResourcesResponse getUnexpectedResources(Collection<Protos.Offer> unusedOffers) {
    Collection<OfferResources> unexpected = unusedOffers.stream()
        .map(offer -> new OfferResources(offer).addAll(offer.getResourcesList().stream()
            // Omit any unreserved resources, and any unrefined pre-reserved resources.
            // In addition, checking for a valid resource_id label is a good sanity check to avoid
            // potentially unreserving any resources that weren't originally created by the SDK.
            // This is in addition to separate filtering in FrameworkScheduler of reserved Marathon volumes.
            .filter(ResourceUtils::hasResourceId)
            .collect(Collectors.toList())))
        .collect(Collectors.toList());
    try {
      recorder.recordCleanupOrUninstall(unexpected);
    } catch (Exception e) { // SUPPRESS CHECKSTYLE IllegalCatch
      // Failed to record the upcoming dereservation. Don't return the resources as unexpected until we can record
      // the dereservation.
      logger.error("Failed to record unexpected resources", e);
      return UnexpectedResourcesResponse.failed(Collections.emptyList());
    }
    return UnexpectedResourcesResponse.processed(unexpected);
  }

  @Override
  protected void processStatusUpdate(Protos.TaskStatus status) {
    stateStore.storeStatus(StateStoreUtils.getTaskName(stateStore, status), status);
  }

  public DeregisterStep getDeregisterStep() {
    return deregisterStubStep;
  }

  /**
   * Time retrieval broken out into a separate object to allow overriding its behavior in tests.
   */
  @VisibleForTesting
  protected static class TimeFetcher {
    protected long getCurrentTimeMillis() {
      return System.currentTimeMillis();
    }
  }
}
