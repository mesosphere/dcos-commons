package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.api.*;
import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.api.types.StringPropertyDeserializer;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluator;
import com.mesosphere.sdk.offer.history.OfferOutcomeTracker;
import com.mesosphere.sdk.scheduler.decommission.DecommissionPlanFactory;
import com.mesosphere.sdk.scheduler.decommission.DecommissionRecorder;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.recovery.*;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.constrain.TimedLaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.constrain.UnconstrainedLaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.monitor.FailureMonitor;
import com.mesosphere.sdk.scheduler.recovery.monitor.NeverFailureMonitor;
import com.mesosphere.sdk.scheduler.recovery.monitor.TimedFailureMonitor;
import com.mesosphere.sdk.specification.ReplacementFailurePolicy;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.*;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This scheduler when provided with a ServiceSpec will deploy the service and recover from encountered faults
 * when possible.  Changes to the ServiceSpec will result in rolling configuration updates, or the creation of
 * new Tasks where applicable.
 */
public class DefaultScheduler extends AbstractScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultScheduler.class);

    private final ServiceSpec serviceSpec;

    private final Collection<Plan> plans;
    private final Optional<RecoveryPlanOverriderFactory> recoveryPlanOverriderFactory;
    private final OfferAccepter offerAccepter;

    private final Collection<Object> resources;
    private final HealthResource healthResource;
    private final PlansResource plansResource;
    private final PodResource podResource;

    private TaskKiller taskKiller;
    private PlanCoordinator planCoordinator;
    private PlanScheduler planScheduler;

    private final OfferOutcomeTracker offerOutcomeTracker;

    /**
     * Creates a new {@link SchedulerBuilder} based on the provided {@link ServiceSpec} describing the service,
     * including details such as the service name, the pods/tasks to be deployed, and the plans describing how the
     * deployment should be organized.
     */
    public static SchedulerBuilder newBuilder(
            ServiceSpec serviceSpec, SchedulerConfig schedulerConfig) throws PersisterException {
        return new SchedulerBuilder(serviceSpec, schedulerConfig);
    }

    /**
     * Creates a new {@link SchedulerBuilder} based on the provided {@link ServiceSpec} describing the service,
     * including details such as the service name, the pods/tasks to be deployed, and the plans describing how the
     * deployment should be organized.
     */
    @VisibleForTesting
    public static SchedulerBuilder newBuilder(
            ServiceSpec serviceSpec, SchedulerConfig schedulerConfig, Persister persister) throws PersisterException {
        return new SchedulerBuilder(serviceSpec, schedulerConfig, persister);
    }

    /**
     * Creates a new DefaultScheduler. See information about parameters in {@link SchedulerBuilder}.
     */
    protected DefaultScheduler(
            ServiceSpec serviceSpec,
            SchedulerConfig schedulerConfig,
            Collection<Object> customResources,
            Collection<Plan> plans,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Map<String, EndpointProducer> customEndpointProducers,
            Optional<RecoveryPlanOverriderFactory> recoveryPlanOverriderFactory) {
        super(stateStore, configStore, schedulerConfig);
        this.serviceSpec = serviceSpec;
        this.plans = plans;
        this.recoveryPlanOverriderFactory = recoveryPlanOverriderFactory;
        this.offerAccepter = new OfferAccepter(Arrays.asList(
                new PersistentLaunchRecorder(stateStore, serviceSpec),
                Metrics.OperationsCounter.getInstance()));

        this.resources = new ArrayList<>();
        this.resources.addAll(customResources);
        this.resources.add(new ArtifactResource(configStore));
        this.resources.add(new ConfigResource<>(configStore));
        EndpointsResource endpointsResource = new EndpointsResource(stateStore, serviceSpec.getName());
        for (Map.Entry<String, EndpointProducer> entry : customEndpointProducers.entrySet()) {
            endpointsResource.setCustomEndpoint(entry.getKey(), entry.getValue());
        }
        this.resources.add(endpointsResource);
        this.plansResource = new PlansResource();
        this.resources.add(this.plansResource);
        this.healthResource = new HealthResource();
        this.resources.add(this.healthResource);
        this.podResource = new PodResource(
                stateStore,
                serviceSpec.getName(),
                new DefaultTaskFailureListener(stateStore, configStore));
        this.resources.add(this.podResource);
        this.resources.add(new StateResource(stateStore, new StringPropertyDeserializer()));

        this.offerOutcomeTracker = new OfferOutcomeTracker();
        this.resources.add(new OfferOutcomeResource(offerOutcomeTracker));
    }

    @Override
    public Collection<Object> getResources() {
        return resources;
    }

    @Override
    protected PlanCoordinator initialize(SchedulerDriver driver) throws Exception {
        // NOTE: We wait until this point to perform any work using configStore/stateStore.
        // We specifically avoid writing any data to ZK before registered() has been called.

        this.taskKiller = new TaskKiller(driver);

        PlanManager deploymentPlanManager =
                DefaultPlanManager.createProceeding(SchedulerUtils.getDeployPlan(plans).get());
        PlanManager recoveryPlanManager = getRecoveryPlanManager();
        planCoordinator = buildPlanCoordinator(deploymentPlanManager, recoveryPlanManager);
        planScheduler = new DefaultPlanScheduler(
                        offerAccepter,
                        new OfferEvaluator(
                                stateStore,
                                offerOutcomeTracker,
                                serviceSpec.getName(),
                                configStore.getTargetConfig(),
                                schedulerConfig,
                                Capabilities.getInstance().supportsDefaultExecutor()),
                        stateStore,
                        taskKiller);
        killUnneededTasks(stateStore, taskKiller, PlanUtils.getLaunchableTasks(plans));

        plansResource.setPlanManagers(planCoordinator.getPlanManagers());
        healthResource.setHealthyPlanManagers(Arrays.asList(deploymentPlanManager, recoveryPlanManager));
        podResource.setTaskKiller(taskKiller);
        return planCoordinator;
    }

    private static void killUnneededTasks(
            StateStore stateStore, TaskKiller taskKiller, Set<String> taskToDeployNames) {
        Set<Protos.TaskInfo> taskInfos = stateStore.fetchTasks().stream()
                .filter(taskInfo -> !taskToDeployNames.contains(taskInfo.getName()))
                .collect(Collectors.toSet());

        Set<Protos.TaskID> taskIds = taskInfos.stream()
                .map(taskInfo -> taskInfo.getTaskId())
                .collect(Collectors.toSet());

        // Clear the TaskIDs from the TaskInfos so we drop all future TaskStatus Messages
        Set<Protos.TaskInfo> cleanedTaskInfos = taskInfos.stream()
                .map(taskInfo -> taskInfo.toBuilder())
                .map(builder -> builder.setTaskId(Protos.TaskID.newBuilder().setValue("")).build())
                .collect(Collectors.toSet());

        // Remove both TaskInfo and TaskStatus, then store the cleaned TaskInfo one at a time to limit damage in the
        // event of an untimely scheduler crash
        for (Protos.TaskInfo taskInfo : cleanedTaskInfos) {
            stateStore.clearTask(taskInfo.getName());
            stateStore.storeTasks(Arrays.asList(taskInfo));
        }

        taskIds.forEach(taskID -> taskKiller.killTask(taskID));

        for (Protos.TaskInfo taskInfo : stateStore.fetchTasks()) {
            GoalStateOverride.Status overrideStatus = stateStore.fetchGoalOverrideStatus(taskInfo.getName());
            if (overrideStatus.progress == GoalStateOverride.Progress.PENDING) {
                // Enabling or disabling an override was triggered, but the task kill wasn't processed so that the
                // change in override could take effect. Kill the task so that it can enter (or exit) the override. The
                // override status will then be marked IN_PROGRESS once we have received the terminal TaskStatus.
                taskKiller.killTask(taskInfo.getTaskId());
            }
        }
    }

    private PlanManager getRecoveryPlanManager() {
        List<RecoveryPlanOverrider> overrideRecoveryPlanManagers = new ArrayList<>();
        if (recoveryPlanOverriderFactory.isPresent()) {
            LOGGER.info("Adding overriding recovery plan manager.");
            overrideRecoveryPlanManagers.add(recoveryPlanOverriderFactory.get().create(stateStore, plans));
        }
        final LaunchConstrainer launchConstrainer;
        final FailureMonitor failureMonitor;
        if (serviceSpec.getReplacementFailurePolicy().isPresent()) {
            ReplacementFailurePolicy failurePolicy = serviceSpec.getReplacementFailurePolicy().get();
            launchConstrainer = new TimedLaunchConstrainer(
                    Duration.ofMinutes(failurePolicy.getMinReplaceDelayMin()));
            failureMonitor = new TimedFailureMonitor(
                    Duration.ofMinutes(failurePolicy.getPermanentFailureTimoutMin()),
                    stateStore,
                    configStore);
        } else {
            launchConstrainer = new UnconstrainedLaunchConstrainer();
            failureMonitor = new NeverFailureMonitor();
        }
        return new DefaultRecoveryPlanManager(
                stateStore,
                configStore,
                PlanUtils.getLaunchableTasks(plans),
                launchConstrainer,
                failureMonitor,
                overrideRecoveryPlanManagers);
    }

    private PlanCoordinator buildPlanCoordinator(
            PlanManager deploymentPlanManager,
            PlanManager recoveryPlanManager) throws ConfigStoreException {
        final Collection<PlanManager> planManagers = new ArrayList<>();
        planManagers.add(deploymentPlanManager);
        planManagers.add(recoveryPlanManager);

        // If decommissioning nodes, set up decommission plan:
        DecommissionPlanFactory decommissionPlanFactory =
                new DecommissionPlanFactory(serviceSpec, stateStore, taskKiller);
        Optional<Plan> decommissionPlan = decommissionPlanFactory.getPlan();
        if (decommissionPlan.isPresent()) {
            // Set things up for a decommission operation.
            offerAccepter.addRecorder(new DecommissionRecorder(stateStore, decommissionPlanFactory.getResourceSteps()));
            planManagers.add(DefaultPlanManager.createProceeding(decommissionPlan.get()));
        }

        // Other custom plan managers
        planManagers.addAll(plans.stream()
                .filter(plan -> !plan.isDeployPlan())
                .map(DefaultPlanManager::createInterrupted)
                .collect(Collectors.toList()));

        return new DefaultPlanCoordinator(planManagers);
    }

    @Override
    protected void processOffers(SchedulerDriver driver, List<Protos.Offer> offers, Collection<Step> steps) {
        // See which offers are useful to the plans.
        List<Protos.OfferID> planOffers = new ArrayList<>();
        planOffers.addAll(planScheduler.resourceOffers(driver, offers, steps));
        List<Protos.Offer> unusedOffers = OfferUtils.filterOutAcceptedOffers(offers, planOffers);

        // Resource Cleaning:
        // A ResourceCleaner ensures that reserved Resources are not leaked.  It is possible that an Agent may
        // become inoperable for long enough that Tasks resident there were relocated.  However, this Agent may
        // return at a later point and begin offering reserved Resources again.  To ensure that these unexpected
        // reserved Resources are returned to the Mesos Cluster, the Resource Cleaner performs all necessary
        // UNRESERVE and DESTROY (in the case of persistent volumes) Operations.
        // Note: If there are unused reserved resources on a dirtied offer, then it will be cleaned in the next
        // offer cycle.
        // Note: We reconstruct the instance every cycle to trigger internal reevaluation of expected resources.
        ResourceCleanerScheduler cleanerScheduler =
                new ResourceCleanerScheduler(new DefaultResourceCleaner(stateStore), offerAccepter);
        List<Protos.OfferID> cleanerOffers = cleanerScheduler.resourceOffers(driver, unusedOffers);
        unusedOffers = OfferUtils.filterOutAcceptedOffers(unusedOffers, cleanerOffers);

        // Decline remaining offers.
        if (!unusedOffers.isEmpty()) {
            OfferUtils.declineLong(driver, unusedOffers);
        }

        if (offers.isEmpty()) {
            LOGGER.info("0 Offers processed.");
        } else {
            LOGGER.info("{} Offer{} processed:\n"
                    + "  {} accepted by Plans: {}\n"
                    + "  {} accepted by Resource Cleaner: {}\n"
                    + "  {} declined: {}",
                    offers.size(),
                    offers.size() == 1 ? "" : "s",
                    planOffers.size(),
                    planOffers.stream().map(Protos.OfferID::getValue).collect(Collectors.toList()),
                    cleanerOffers.size(),
                    cleanerOffers.stream().map(Protos.OfferID::getValue).collect(Collectors.toList()),
                    unusedOffers.size(),
                    unusedOffers.stream().map(offer -> offer.getId().getValue()).collect(Collectors.toList()));
        }
    }

    @Override
    protected void processStatusUpdate(Protos.TaskStatus status) {
        // Store status, then pass status to PlanManager => Plan => Steps
        String taskName = StateStoreUtils.getTaskName(stateStore, status);

        // StateStore updates:
        // - TaskStatus
        // - Override status (if applicable)
        stateStore.storeStatus(taskName, status);

        // Notify plans of status update:
        planCoordinator.getPlanManagers().forEach(planManager -> planManager.update(status));

        // If the TaskStatus contains an IP Address, store it as a property in the StateStore.
        // We expect the TaskStatus to contain an IP address in both Host or CNI networking.
        // Currently, we are always _missing_ the IP Address on TASK_LOST. We always expect it on TASK_RUNNINGs
        if (status.hasContainerStatus() &&
                status.getContainerStatus().getNetworkInfosCount() > 0 &&
                status.getContainerStatus().getNetworkInfosList().stream()
                        .anyMatch(networkInfo -> networkInfo.getIpAddressesCount() > 0)) {
            // Map the TaskStatus to a TaskInfo. The map will throw a StateStoreException if no such TaskInfo exists.
            try {
                StateStoreUtils.storeTaskStatusAsProperty(stateStore, taskName, status);
            } catch (StateStoreException e) {
                LOGGER.warn("Unable to store network info for status update: " + status, e);
            }
        }
    }

    @VisibleForTesting
    PlanCoordinator getPlanCoordinator() {
        return planCoordinator;
    }
}
