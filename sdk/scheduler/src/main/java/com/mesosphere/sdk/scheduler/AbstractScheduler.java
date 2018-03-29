package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.framework.FrameworkConfig;
import com.mesosphere.sdk.framework.FrameworkScheduler;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Abstract main scheduler class that ties together the main pieces of a SDK Scheduler process.
 * Handles interaction with Mesos via an embedded {@link AbstractScheduler.MesosScheduler} object.
 */
public abstract class AbstractScheduler {

    private static final Logger LOGGER = LoggingUtils.getLogger(AbstractScheduler.class);

    protected final FrameworkStore frameworkStore;
    protected final ServiceSpec serviceSpec;
    protected final StateStore stateStore;
    protected final ConfigStore<ServiceSpec> configStore;
    protected final SchedulerConfig schedulerConfig;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Optional<PlanCustomizer> planCustomizer;

    private final FrameworkScheduler frameworkScheduler;

    private final Object inProgressLock = new Object();
    private final Set<Protos.OfferID> offersInProgress = new HashSet<>();

    /**
     * Creates a new AbstractScheduler given a {@link StateStore}.
     */
    protected AbstractScheduler(
            ServiceSpec serviceSpec,
            FrameworkStore frameworkStore,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            FrameworkConfig frameworkConfig,
            SchedulerConfig schedulerConfig,
            Optional<PlanCustomizer> planCustomizer) {
        this.serviceSpec = serviceSpec;
        this.frameworkStore = frameworkStore;
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.schedulerConfig = schedulerConfig;
        this.planCustomizer = planCustomizer;
        this.frameworkScheduler = new FrameworkScheduler(
                frameworkConfig.getAllResourceRoles(), schedulerConfig, frameworkStore, stateStore, this);
    }

    /**
     * Returns the service spec for this service.
     */
    public ServiceSpec getServiceSpec() {
        return serviceSpec;
    }

    /**
     * Starts any internal threads to be used by the service.
     * Must be called after construction, once, in order for work to proceed.
     *
     * @return this
     */
    public AbstractScheduler start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("start() can only be called once");
        }

        if (planCustomizer.isPresent()) {
            for (PlanManager planManager : getPlanCoordinator().getPlanManagers()) {
                if (planManager.getPlan().isRecoveryPlan()) {
                    continue;
                }

                if (planManager.getPlan().isDeployPlan() && this instanceof UninstallScheduler) {
                    planManager.setPlan(planCustomizer.get().updateUninstallPlan(planManager.getPlan()));
                } else {
                    planManager.setPlan(planCustomizer.get().updatePlan(planManager.getPlan()));
                }
            }
        }

        return this;
    }

    /**
     * Returns a Mesos API {@link Scheduler} object to be registered with Mesos, or an empty {@link Optional} if Mesos
     * registration should not be performed.
     */
    public Optional<Scheduler> getMesosScheduler() {
        return Optional.of(frameworkScheduler);
    }

    protected void markApiServerStarted() {
        frameworkScheduler.setReadyToAcceptOffers();
    }

    /**
     * All offers must have been presented to resourceOffers() before calling this.  This call will block until all
     * offers have been processed.
     *
     * @throws InterruptedException if waiting for offers to be processed is interrupted
     * @throws IllegalStateException if offers were not processed in a reasonable amount of time
     */
    @VisibleForTesting
    public void awaitOffersProcessed() throws InterruptedException {
        final int totalDurationMs = 5000;
        final int sleepDurationMs = 100;
        for (int i = 0; i < totalDurationMs / sleepDurationMs; ++i) {
            synchronized (inProgressLock) {
                if (offersInProgress.isEmpty()) {
                    LOGGER.info("All offers processed.");
                    return;
                }
                LOGGER.warn("Offers in progress {} is non-empty, sleeping for {}ms ...",
                        offersInProgress, sleepDurationMs);
            }
            Thread.sleep(sleepDurationMs);
        }
        throw new IllegalStateException(String.format(
                "Timed out after %dms waiting for offers to be processed", totalDurationMs));
    }

    /**
     * Skips the creation of the API server and marks it as "started". In order for this to have any effect, it must
     * be called before {@link #start()}.
     *
     * @return this
     */
    @VisibleForTesting
    public AbstractScheduler disableApiServer() {
        markApiServerStarted();
        return this;
    }

    /**
     * Forces the Scheduler to run in a synchronous/single-threaded mode for tests. To have any effect, this must be
     * called before calling {@link #start()}.
     *
     * @return this
     */
    @VisibleForTesting
    public AbstractScheduler disableThreading() {
        frameworkScheduler.disableThreading();
        return this;
    }

    /**
     * Returns the plans defined for this scheduler. Useful for scheduler tests.
     */
    @VisibleForTesting
    public Collection<Plan> getPlans() {
        return getPlanCoordinator().getPlanManagers().stream()
                .map(planManager -> planManager.getPlan())
                .collect(Collectors.toList());
    }

    /**
     * Returns a list of API resources to be served by the scheduler to the local cluster.
     */
    public abstract Collection<Object> getResources();

    /**
     * Returns the {@link PlanCoordinator}.
     */
    public abstract PlanCoordinator getPlanCoordinator();

    /**
     * Provides a callback to indicate that the scheduler has registered with Mesos.
     */
    public abstract void registeredWithMesos();

    /**
     * The abstract scheduler will periodically call this method with a list of available offers, which may be empty.
     */
    public abstract void processOffers(List<Protos.Offer> offers, Collection<Step> steps);

    /**
     * Handles a task status update which was received from Mesos. This call is executed on a separate thread which is
     * run by the Mesos Scheduler Driver.
     */
    public abstract void processStatusUpdate(Protos.TaskStatus status) throws Exception;
}
