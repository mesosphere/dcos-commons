package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.storage.Persister;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Class which sets up and executes the correct {@link ServiceScheduler} instance.
 */
public class FrameworkRunner {
    private static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;
    private static final Logger LOGGER = LoggerFactory.getLogger(FrameworkRunner.class);

    private final SchedulerConfig schedulerConfig;
    private final FrameworkConfig frameworkConfig;

    /**
     * Creates a new instance and does some internal initialization.
     *
     * @param schedulerConfig scheduler config object to use for the process
     * @param frameworkConfig settings to use for registering the framework
     */
    public FrameworkRunner(SchedulerConfig schedulerConfig, FrameworkConfig frameworkConfig) {
        this.schedulerConfig = schedulerConfig;
        this.frameworkConfig = frameworkConfig;
    }

    // NOTE: in multi-service case, use a single MultiMesosEventClient.
    public void registerAndRunFramework(Persister persister, MesosEventClient mesosEventClient) {
        FrameworkScheduler frameworkScheduler = new FrameworkScheduler(persister, mesosEventClient);
        MesosEventClient.ResourceServer server = SchedulerApiServer.start(schedulerConfig, new Runnable() {
            @Override
            public void run() {
                frameworkScheduler.setReadyToAcceptOffers();
            }
        });
        mesosEventClient.setResourceServer(server);

        Protos.FrameworkInfo frameworkInfo = getFrameworkInfo(frameworkScheduler.fetchFrameworkId());
        LOGGER.info("Registering framework: {}", TextFormat.shortDebugString(frameworkInfo));
        String zkUri = String.format("zk://%s/mesos", frameworkConfig.zookeeperConnection);
        Protos.Status status = new SchedulerDriverFactory()
                .create(frameworkScheduler, frameworkInfo, zkUri, schedulerConfig)
                .run();
        LOGGER.error("Scheduler driver exited with status: {}", status);
        // DRIVER_STOPPED will occur when we call stop(boolean) during uninstall.
        // When this happens, we want to continue running so that we can advertise that the uninstall plan is complete.
        if (status != Protos.Status.DRIVER_STOPPED) {
            SchedulerUtils.hardExit(SchedulerErrorCode.DRIVER_EXITED);
        }
    }

    @VisibleForTesting
    Protos.FrameworkInfo getFrameworkInfo(Optional<Protos.FrameworkID> frameworkId) {
        Protos.FrameworkInfo.Builder fwkInfoBuilder = Protos.FrameworkInfo.newBuilder()
                .setName(frameworkConfig.frameworkName)
                .setPrincipal(frameworkConfig.principal)
                .setUser(frameworkConfig.user)
                .setFailoverTimeout(TWO_WEEK_SEC)
                .setCheckpoint(true);

        // The framework ID is not available when we're being started for the first time.
        frameworkId.ifPresent(fwkInfoBuilder::setId);

        if (frameworkConfig.preReservedRoles.isEmpty()) {
            setRole(fwkInfoBuilder, frameworkConfig.role);
        } else {
            fwkInfoBuilder.addCapabilitiesBuilder()
                    .setType(Protos.FrameworkInfo.Capability.Type.MULTI_ROLE);
            fwkInfoBuilder
                    .addRoles(frameworkConfig.role)
                    .addAllRoles(frameworkConfig.preReservedRoles);
        }

        if (!StringUtils.isEmpty(frameworkConfig.webUrl)) {
            fwkInfoBuilder.setWebuiUrl(frameworkConfig.webUrl);
        }

        Capabilities capabilities = Capabilities.getInstance();
        if (capabilities.supportsGpuResource() && frameworkConfig.enableGpuResources) {
            fwkInfoBuilder.addCapabilitiesBuilder()
                    .setType(Protos.FrameworkInfo.Capability.Type.GPU_RESOURCES);
        }
        if (capabilities.supportsPreReservedResources()) {
            fwkInfoBuilder.addCapabilitiesBuilder()
                    .setType(Protos.FrameworkInfo.Capability.Type.RESERVATION_REFINEMENT);
        }
        if (capabilities.supportsRegionAwareness(schedulerConfig)) {
            fwkInfoBuilder.addCapabilitiesBuilder()
                    .setType(Protos.FrameworkInfo.Capability.Type.REGION_AWARE);
        }

        return fwkInfoBuilder.build();
    }

    @SuppressWarnings("deprecation") // mute warning for FrameworkInfo.setRole()
    private static void setRole(Protos.FrameworkInfo.Builder fwkInfoBuilder, String role) {
        fwkInfoBuilder.setRole(role);
    }
}
