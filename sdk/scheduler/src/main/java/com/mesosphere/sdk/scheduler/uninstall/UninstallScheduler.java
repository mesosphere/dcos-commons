package com.mesosphere.sdk.scheduler.uninstall;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.http.endpoints.DeprecatedPlanResource;
import com.mesosphere.sdk.http.endpoints.HealthResource;
import com.mesosphere.sdk.http.endpoints.PlansResource;
import com.mesosphere.sdk.http.types.EndpointProducer;
import com.mesosphere.sdk.http.types.PlanInfo;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.OfferResources;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This scheduler uninstalls a service and releases all of its resources.
 */
public class UninstallScheduler extends AbstractScheduler {

    private final Logger logger = LoggingUtils.getLogger(getClass());

    private final ConfigStore<ServiceSpec> configStore;
    private final UninstallRecorder recorder;
    // This step is used in the deploy plan to represent the unregister operation that's handled in FrameworkRunner.
    // We want to ensure that the deploy plan is only marked complete after deregistration has been completed.
    private final DeregisterStep deregisterStubStep;
    private final PlanManager uninstallPlanManager;

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
            Optional<String> namespace) {
        this(serviceSpec, stateStore, configStore, schedulerConfig, planCustomizer, namespace, Optional.empty());
    }

    @VisibleForTesting
    protected UninstallScheduler(
            ServiceSpec serviceSpec,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            SchedulerConfig schedulerConfig,
            Optional<PlanCustomizer> planCustomizer,
            Optional<String> namespace,
            Optional<SecretsClient> customSecretsClientForTests) {
        super(serviceSpec, stateStore, planCustomizer, namespace);
        this.configStore = configStore;

        if (!StateStoreUtils.isUninstalling(stateStore)) {
            logger.info("Service has been told to uninstall. Marking this in the persistent state store. " +
                    "Uninstall cannot be canceled once triggered.");
            StateStoreUtils.setUninstalling(stateStore);
        }

        // Construct a plan for uninstalling any remaining resources
        UninstallPlanFactory planFactory =
                new UninstallPlanFactory(serviceSpec, stateStore, schedulerConfig, customSecretsClientForTests);
        this.recorder = new UninstallRecorder(stateStore, planFactory.getResourceCleanupSteps());
        this.deregisterStubStep = planFactory.getDeregisterStep();

        this.uninstallPlanManager = DefaultPlanManager.createProceeding(planFactory.getPlan());
        try {
            logger.info("Uninstall plan set to: {}",
                    SerializationUtils.toJsonString(PlanInfo.forPlan(planFactory.getPlan())));
        } catch (IOException e) {
            logger.error("Failed to deserialize uninstall plan.");
        }
    }

    @Override
    public Collection<Object> getHTTPEndpoints() {
        PlansResource plansResource = new PlansResource(Collections.singletonList(uninstallPlanManager));
        return Arrays.asList(
                plansResource,
                new DeprecatedPlanResource(plansResource),
                new HealthResource(Collections.singletonList(uninstallPlanManager)));
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
        logger.info("Uninstall scheduler registered with Mesos.");
    }

    @Override
    public void unregistered() {
        // Mark the the last step of the uninstall plan as complete.
        // Cosmos will then see that the plan is complete and remove us from Marathon.
        deregisterStubStep.setComplete();
    }

    @Override
    public ClientStatusResponse getClientStatus() {
        if (deregisterStubStep.isRunning() || deregisterStubStep.isComplete()) {
            // The service resources have been deleted and all that's left is the final deregister operation. After we
            // return uninstalled(), upstream will finish the uninstall by doing one of the following:
            // - Single-service: Upstream will stop/remove the framework, then unregistered() will be called.
            // - Multi-service: Upstream will remove us from the list of services without calling unregistered().
            return ClientStatusResponse.uninstalled();
        } else {
            // Note: We return uninstalling() instead of reserving(), because the latter is mainly about preventing two
            // services from growing in the cluster at the same time. That could lead to a deadlock across them. In the
            // uninstall case the service is strictly shrinking, so there isn't any reason to get exclusive deployment.
            return ClientStatusResponse.uninstalling();
        }
    }

    @Override
    protected OfferResponse processOffers(Collection<Protos.Offer> offers, Collection<Step> steps) {
        // Get candidate steps to be scheduled
        if (!steps.isEmpty()) {
            logger.info("Attempting to process {} candidates from uninstall plan: {}",
                    steps.size(), steps.stream().map(Element::getName).collect(Collectors.toList()));
            steps.forEach(Step::start);
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
                        // Omit unreserved resources:
                        .filter(resource -> ResourceUtils.getReservation(resource).isPresent())
                        .collect(Collectors.toList())))
                .collect(Collectors.toList());
        try {
            recorder.recordResources(unexpected);
            return UnexpectedResourcesResponse.processed(unexpected);
        } catch (Exception e) {
            // Failed to record the upcoming dereservation. Don't return the resources as unexpected until we can record
            // the dereservation.
            logger.error("Failed to record unexpected resources", e);
            return UnexpectedResourcesResponse.failed(Collections.emptyList());
        }
    }

    @Override
    protected void processStatusUpdate(Protos.TaskStatus status) throws Exception {
        stateStore.storeStatus(StateStoreUtils.getTaskName(stateStore, status), status);
    }

    public DeregisterStep getDeregisterStep() {
        return deregisterStubStep;
    }
}
