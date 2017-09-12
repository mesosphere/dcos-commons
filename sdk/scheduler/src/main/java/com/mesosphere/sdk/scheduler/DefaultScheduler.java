package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.api.*;
import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.api.types.StringPropertyDeserializer;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluator;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
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
    private final TaskKiller taskKiller;
    private final OfferAccepter offerAccepter;

    private final Collection<Object> resources;
    private final PlansResource plansResource;
    private final PodResource podResource;

    private SchedulerDriver driver;
    private PlanCoordinator planCoordinator;

    /**
     * Creates a new {@link SchedulerBuilder} based on the provided {@link ServiceSpec} describing the service, including
     * details such as the service name, the pods/tasks to be deployed, and the plans describing how the deployment
     * should be organized.
     */
    public static SchedulerBuilder newBuilder(ServiceSpec serviceSpec, SchedulerFlags schedulerFlags)
            throws PersisterException {
        return new SchedulerBuilder(serviceSpec, schedulerFlags);
    }

    /**
     * Creates a new {@link SchedulerBuilder} based on the provided {@link ServiceSpec} describing the service, including
     * details such as the service name, the pods/tasks to be deployed, and the plans describing how the deployment
     * should be organized.
     */
    @VisibleForTesting
    public static SchedulerBuilder newBuilder(
            ServiceSpec serviceSpec,
            SchedulerFlags schedulerFlags,
            Persister persister) throws PersisterException {
        return new SchedulerBuilder(serviceSpec, schedulerFlags, persister);
    }

    /**
     * Creates a new DefaultScheduler. See information about parameters in {@link SchedulerBuilder}.
     */
    protected DefaultScheduler(
            ServiceSpec serviceSpec,
            SchedulerFlags schedulerFlags,
            Collection<Object> customResources,
            Collection<Plan> plans,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Map<String, EndpointProducer> customEndpointProducers,
            Optional<RecoveryPlanOverriderFactory> recoveryPlanOverriderFactory) {
        super(stateStore, configStore, schedulerFlags);
        this.serviceSpec = serviceSpec;
        this.plans = plans;
        this.recoveryPlanOverriderFactory = recoveryPlanOverriderFactory;
        this.taskKiller = new DefaultTaskKiller(new DefaultTaskFailureListener(stateStore, configStore));
        this.offerAccepter = new OfferAccepter(
                Collections.singletonList(new PersistentLaunchRecorder(stateStore, serviceSpec)));

        resources = new ArrayList<>();
        resources.addAll(customResources);
        resources.add(new ArtifactResource(configStore));
        resources.add(new ConfigResource<>(configStore));
        EndpointsResource endpointsResource = new EndpointsResource(stateStore, serviceSpec.getName());
        for (Map.Entry<String, EndpointProducer> entry : customEndpointProducers.entrySet()) {
            endpointsResource.setCustomEndpoint(entry.getKey(), entry.getValue());
        }
        resources.add(endpointsResource);
        this.plansResource = new PlansResource();
        resources.add(this.plansResource);
        this.podResource = new PodResource(stateStore);
        resources.add(podResource);
        resources.add(new StateResource(stateStore, new StringPropertyDeserializer()));
    }

    @Override
    public Collection<Object> getResources() {
        return resources;
    }

    @Override
    protected void initialize(SchedulerDriver driver) throws Exception {
        this.driver = driver;

        // NOTE: We wait until this point to perform any work using configStore/stateStore.
        // We specifically avoid writing any data to ZK before registered() has been called.

        taskKiller.setSchedulerDriver(driver);
        planCoordinator = getPlanCoordinator(taskKiller, offerAccepter);
        killUnneededTasks(stateStore, taskKiller, PlanUtils.getLaunchableTasks(plans));

        plansResource.setPlanManagers(planCoordinator.getPlanManagers());
        podResource.setTaskKiller(taskKiller);
    }

    private PlanCoordinator getPlanCoordinator(TaskKiller taskKiller, OfferAccepter offerAccepter)
            throws ConfigStoreException {
        final Collection<PlanManager> planManagers = new ArrayList<>();

        // 1a. Deployment plan manager
        PlanManager deploymentPlanManager = new DefaultPlanManager(SchedulerUtils.getDeployPlan(plans).get());
        // All plans are initially created with an interrupted strategy. We generally don't want the deployment plan to
        // start out interrupted. CanaryStrategy is an exception which explicitly indicates that the deployment plan
        // should start out interrupted, but CanaryStrategies are only applied to individual Phases, not the Plan as a
        // whole.
        deploymentPlanManager.getPlan().proceed();
        planManagers.add(deploymentPlanManager);

        // 1b. Recovery plan manager
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
        planManagers.add(new DefaultRecoveryPlanManager(
                stateStore,
                configStore,
                PlanUtils.getLaunchableTasks(plans),
                launchConstrainer,
                failureMonitor,
                overrideRecoveryPlanManagers));

        // 1c. Other (non-deploy) plans
        planManagers.addAll(plans.stream()
                .filter(plan -> !plan.isDeployPlan())
                .map(DefaultPlanManager::new)
                .collect(Collectors.toList()));

        // 2. Finally, the Plan Scheduler
        PlanScheduler planScheduler = new DefaultPlanScheduler(
                offerAccepter,
                new OfferEvaluator(
                        stateStore,
                        serviceSpec.getName(),
                        configStore.getTargetConfig(),
                        schedulerFlags,
                        Capabilities.getInstance().supportsDefaultExecutor()),
                stateStore,
                taskKiller);

        return new DefaultPlanCoordinator(planManagers, planScheduler);
    }

    private static void killUnneededTasks(StateStore stateStore, TaskKiller taskKiller, Set<String> taskToDeployNames) {
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

        taskIds.forEach(taskID -> taskKiller.killTask(taskID, RecoveryType.NONE));
    }

    @Override
    protected void processOfferSet(List<Protos.Offer> offers) {
        List<Protos.Offer> localOffers = new ArrayList<>(offers);

        // Coordinate amongst all the plans via PlanCoordinator.
        final List<Protos.OfferID> acceptedOffers = new ArrayList<>();
        acceptedOffers.addAll(planCoordinator.processOffers(driver, localOffers));
        LOGGER.info("Offers accepted by plan coordinator: {}",
                acceptedOffers.stream().map(Protos.OfferID::getValue).collect(Collectors.toList()));

        List<Protos.Offer> unusedOffers = OfferUtils.filterOutAcceptedOffers(localOffers, acceptedOffers);

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
        List<Protos.OfferID> cleanedOffers = cleanerScheduler.resourceOffers(driver, unusedOffers);
        LOGGER.info("Offers accepted by resource cleaner: {}", cleanedOffers);
        acceptedOffers.addAll(cleanedOffers);

        LOGGER.info("Total accepted offers: {}",
                acceptedOffers.stream().map(Protos.OfferID::getValue).collect(Collectors.toList()));

        unusedOffers = OfferUtils.filterOutAcceptedOffers(localOffers, acceptedOffers);
        LOGGER.info("Unused offers to be declined: {}",
                unusedOffers.stream().map(offer -> offer.getId().getValue()).collect(Collectors.toList()));

        // Decline remaining offers.
        OfferUtils.declineOffers(driver, unusedOffers);
    }

    @Override
    protected void processStatusUpdate(Protos.TaskStatus status) {
        eventBus.post(status);

        // Store status, then pass status to PlanManager => Plan => Steps
        try {
            String taskName = StateStoreUtils.getTaskName(stateStore, status);
            Optional<Protos.TaskStatus> lastStatus = stateStore.fetchStatus(taskName);

            stateStore.storeStatus(taskName, status);
            planCoordinator.getPlanManagers().forEach(planManager -> planManager.update(status));
            reconciler.update(status);

            if (lastStatus.isPresent() &&
                    AuxLabelAccess.isInitialLaunch(lastStatus.get()) &&
                    TaskUtils.isRecoveryNeeded(status)) {
                // The initial launch of this task failed. Give up and try again with a clean slate.
                LOGGER.warn("Task {} appears to have failed its initial launch. Marking pod for permanent recovery. " +
                        "Prior status was: {}",
                        taskName, TextFormat.shortDebugString(lastStatus.get()));
                taskKiller.killTask(status.getTaskId(), RecoveryType.PERMANENT);
            }

            // If the TaskStatus contains an IP Address, store it as a property in the StateStore.
            // We expect the TaskStatus to contain an IP address in both Host or CNI networking.
            // Currently, we are always _missing_ the IP Address on TASK_LOST. We always expect it
            // on TASK_RUNNINGs
            if (status.hasContainerStatus() &&
                    status.getContainerStatus().getNetworkInfosCount() > 0 &&
                    status.getContainerStatus().getNetworkInfosList().stream()
                            .anyMatch(networkInfo -> networkInfo.getIpAddressesCount() > 0)) {
                // Map the TaskStatus to a TaskInfo. The map will throw a StateStoreException if no such
                // TaskInfo exists.
                try {
                    StateStoreUtils.storeTaskStatusAsProperty(stateStore, taskName, status);
                } catch (StateStoreException e) {
                    LOGGER.warn("Unable to store network info for status update: " + status, e);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to update TaskStatus received from Mesos. "
                    + "This may be expected if Mesos sent stale status information: " + status, e);
        }
    }

    @Override
    protected Collection<PlanManager> getPlanManagers() {
        return planCoordinator.getPlanManagers();
    }
}
