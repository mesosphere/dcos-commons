package com.mesosphere.sdk.framework;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.http.EndpointUtils;
import com.mesosphere.sdk.http.endpoints.HealthResource;
import com.mesosphere.sdk.http.endpoints.PlansResource;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.MesosEventClient;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.DefaultPlan;
import com.mesosphere.sdk.scheduler.plan.DefaultPlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.DefaultPlanManager;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Class which sets up and executes the correct {@link AbstractScheduler} instance.
 */
public class FrameworkRunner {
  /**
   * Empty complete deploy plan to be used if the scheduler is uninstalling and was launched
   * in a finished state.
   */
  @VisibleForTesting
  static final Plan EMPTY_DEPLOY_PLAN = new DefaultPlan(
      Constants.DEPLOY_PLAN_NAME,
      Collections.emptyList()
  );

  private static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;

  private static final Logger LOGGER = LoggingUtils.getLogger(FrameworkRunner.class);

  private final SchedulerConfig schedulerConfig;

  private final FrameworkConfig frameworkConfig;

  private final boolean usingGpus;

  private final boolean usingRegions;

  /**
   * Creates a new instance and does some internal initialization.
   *
   * @param schedulerConfig scheduler config object to use for the process
   * @param frameworkConfig settings to use for registering the framework
   */
  public FrameworkRunner(
      SchedulerConfig schedulerConfig,
      FrameworkConfig frameworkConfig,
      boolean usingGpus,
      boolean usingRegions)
  {
    this.schedulerConfig = schedulerConfig;
    this.frameworkConfig = frameworkConfig;
    this.usingGpus = usingGpus;
    this.usingRegions = usingRegions;
  }

  @SuppressWarnings("deprecation")
  // mute warning for FrameworkInfo.setRole()
  private static void setRole(Protos.FrameworkInfo.Builder fwkInfoBuilder, String role) {
    fwkInfoBuilder.setRole(role);
  }

  /**
   * Registers the framework with Mesos and starts running the framework.
   * This function should never return.
   */
  public void registerAndRunFramework(Persister persister, MesosEventClient mesosEventClient) {
    // During uninstall, the Framework ID is the last thing to be removed (along with the rest of
    // zk). If it's gone and the framework is still in uninstall mode, and that indicates we
    // previously finished an uninstall and then got restarted before getting pruned from Marathon.
    // If we tried to register again, it would be with an unset framework id, which would in turn
    // result in us registering a new framework with Mesos from scratch. We avoid that situation
    // by instead just running the process in a bare-bones state where it's only serving the
    // endpoints necessary for Cosmos to remove the process it from Marathon, and where it's not
    // actually registering with Mesos.
    if (schedulerConfig.isUninstallEnabled() &&
        !new FrameworkStore(persister).fetchFrameworkId().isPresent())
    {
      LOGGER.info("Not registering with Mesos because uninstall is complete.");

      try {
        // Just in case, try to clear any other remaining data from ZK. In practice there shouldn't
        // be any left?
        PersisterUtils.clearAllData(persister);
      } catch (PersisterException e) {
        throw new IllegalStateException("Unable to clear all data", e);
      }

      runSkeletonScheduler();
      // The skeleton scheduler should never exit. But just in case...:
      ProcessExit.exit(ProcessExit.DRIVER_EXITED);
    }

    FrameworkStore frameworkStore = new FrameworkStore(persister);

    FrameworkScheduler frameworkScheduler = new FrameworkScheduler(
        frameworkConfig.getAllResourceRoles(),
        schedulerConfig,
        persister,
        frameworkStore,
        mesosEventClient);
    // Notify the framework that it can start accepting offers.
    // This is to avoid the following scenario:
    // - We accept an offer/launch a task
    // - The task has config templates to be retrieved from the scheduler HTTP service...
    // - ... but the scheduler hasn't finishing launching its HTTP service
    ApiServer httpServer = ApiServer.start(
        EndpointUtils.toSchedulerAutoIpHostname(
            frameworkConfig.getFrameworkName(),
            schedulerConfig
        ),
        schedulerConfig,
        mesosEventClient.getHTTPEndpoints(),
        frameworkScheduler::setApiServerStarted
    );

    Protos.FrameworkInfo frameworkInfo = getFrameworkInfo(frameworkStore.fetchFrameworkId());
    LOGGER.info("Registering framework: {}", TextFormat.shortDebugString(frameworkInfo));
    String zkUri = String.format("zk://%s/mesos", frameworkConfig.getZookeeperHostPort());
    Protos.Status status = new SchedulerDriverFactory()
        .create(frameworkScheduler, frameworkInfo, zkUri, schedulerConfig)
        .run();
    LOGGER.info("Scheduler driver exited with status: {}", status);
    // DRIVER_STOPPED will occur when we call stop(boolean) during uninstall.
    // When this happens, we want to continue running so that we can advertise that the uninstall
    // plan is complete.
    if (status == Protos.Status.DRIVER_STOPPED) {
      // Following Mesos driver thread exit, attach to the API server thread.
      // It should run indefinitely.
      httpServer.join();
    }
    ProcessExit.exit(ProcessExit.DRIVER_EXITED);
  }

  @VisibleForTesting
  Protos.FrameworkInfo getFrameworkInfo(Optional<Protos.FrameworkID> frameworkId) {
    Protos.FrameworkInfo.Builder fwkInfoBuilder = Protos.FrameworkInfo.newBuilder()
        .setName(frameworkConfig.getFrameworkName())
        .setPrincipal(frameworkConfig.getPrincipal())
        .setUser(frameworkConfig.getUser())
        .setFailoverTimeout(TWO_WEEK_SEC)
        .setCheckpoint(true);

    // The framework ID is not available when we're being started for the first time.
    frameworkId.ifPresent(fwkInfoBuilder::setId);

    // We set to MULTI_ROLE by default and add all the necessary roles.
    fwkInfoBuilder.addCapabilitiesBuilder()
          .setType(Protos.FrameworkInfo.Capability.Type.MULTI_ROLE);

    if (frameworkConfig.getPreReservedRoles().isEmpty()) {
      fwkInfoBuilder.setRole(frameworkConfig.getRole());
    } else {
      fwkInfoBuilder.addAllRoles(frameworkConfig.getAllResourceRoles());
    }

    if (!StringUtils.isEmpty(frameworkConfig.getWebUrl())) {
      fwkInfoBuilder.setWebuiUrl(frameworkConfig.getWebUrl());
    }

    Capabilities capabilities = Capabilities.getInstance();

    if (capabilities.supportsPartitionAwareness()) {
      //required to receive TASK_GONE_BY_OPERATOR and other messages
      fwkInfoBuilder.addCapabilitiesBuilder()
          .setType(Protos.FrameworkInfo.Capability.Type.PARTITION_AWARE);
    }

    if (usingGpus && capabilities.supportsGpuResource()) {
      fwkInfoBuilder.addCapabilitiesBuilder()
          .setType(Protos.FrameworkInfo.Capability.Type.GPU_RESOURCES);
    }
    if (capabilities.supportsPreReservedResources()) {
      fwkInfoBuilder.addCapabilitiesBuilder()
          .setType(Protos.FrameworkInfo.Capability.Type.RESERVATION_REFINEMENT);
    }

    // Only enable if opted-in by the developer or user.
    if (usingRegions && capabilities.supportsDomains()) {
      fwkInfoBuilder.addCapabilitiesBuilder()
          .setType(Protos.FrameworkInfo.Capability.Type.REGION_AWARE);
    }

    return fwkInfoBuilder.build();
  }

  /**
   * Launches a 'skeleton' scheduler which does nothing other than advertise a completed
   * {@code deploy} plan. This is used in cases where the scheduler is fully uninstalled and is
   * just waiting to get removed from Marathon.
   */
  private void runSkeletonScheduler() {
    PlanManager uninstallPlanManager = DefaultPlanManager.createProceeding(EMPTY_DEPLOY_PLAN);

    PlanCoordinator coordinator = new DefaultPlanCoordinator(Optional.empty(),
        Collections.singletonList(uninstallPlanManager));

    // Bare minimum resources to appear healthy/complete to DC/OS:
    Collection<Object> resources = Arrays.asList(
        // /v1/plans/deploy: Invoked by Cosmos to tell whether we can be removed from Marathon.
        //                   This is hard-coded in Cosmos.
        new PlansResource(Collections.singletonList(uninstallPlanManager)),
        // /v1/health: Invoked by Mesos as directed a configured health check in the
        // scheduler Marathon app.
        new HealthResource(coordinator, Optional.empty()));
    ApiServer httpServer = ApiServer.start(
        EndpointUtils.toSchedulerAutoIpHostname(
            frameworkConfig.getFrameworkName(),
            schedulerConfig
        ),
        schedulerConfig,
        resources,
        () -> LOGGER.info("Started trivially healthy API server.")
    );
    httpServer.join();
  }
}
