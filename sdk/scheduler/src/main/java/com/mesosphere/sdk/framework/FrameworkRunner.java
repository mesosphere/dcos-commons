package com.mesosphere.sdk.framework;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.plan.DefaultPlan;
import com.mesosphere.sdk.scheduler.plan.Plan;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import java.util.*;

/**
 * Class which sets up and executes the correct {@link AbstractScheduler} instance.
 *
 * TODO(nickbp): Once *Scheduler is broken up, this will run the Mesos framework thread.
 */
public class FrameworkRunner {
    private static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;

    /**
     * Empty complete deploy plan to be used if the scheduler is uninstalling and was launched in a finished state.
     */
    @VisibleForTesting
    static final Plan EMPTY_DEPLOY_PLAN = new DefaultPlan(Constants.DEPLOY_PLAN_NAME, Collections.emptyList());

    private final FrameworkConfig frameworkConfig;
    private final boolean usingGpus;
    private final boolean usingRegions;

    /**
     * Creates a new instance and does some internal initialization.
     *
     * @param frameworkConfig settings to use for registering the framework
     */
    public FrameworkRunner(
            FrameworkConfig frameworkConfig,
            boolean usingGpus,
            boolean usingRegions) {
        this.frameworkConfig = frameworkConfig;
        this.usingGpus = usingGpus;
        this.usingRegions = usingRegions;
    }

    public Protos.FrameworkInfo getFrameworkInfo(Optional<Protos.FrameworkID> frameworkId) {
        Protos.FrameworkInfo.Builder fwkInfoBuilder = Protos.FrameworkInfo.newBuilder()
                .setName(frameworkConfig.getFrameworkName())
                .setPrincipal(frameworkConfig.getPrincipal())
                .setUser(frameworkConfig.getUser())
                .setFailoverTimeout(TWO_WEEK_SEC)
                .setCheckpoint(true);

        // The framework ID is not available when we're being started for the first time.
        frameworkId.ifPresent(fwkInfoBuilder::setId);

        if (frameworkConfig.getPreReservedRoles().isEmpty()) {
            setRole(fwkInfoBuilder, frameworkConfig.getRole());
        } else {
            fwkInfoBuilder.addCapabilitiesBuilder()
                    .setType(Protos.FrameworkInfo.Capability.Type.MULTI_ROLE);
            fwkInfoBuilder
                    .addRoles(frameworkConfig.getRole())
                    .addAllRoles(frameworkConfig.getPreReservedRoles());
        }

        if (!StringUtils.isEmpty(frameworkConfig.getWebUrl())) {
            fwkInfoBuilder.setWebuiUrl(frameworkConfig.getWebUrl());
        }

        Capabilities capabilities = Capabilities.getInstance();
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

    @SuppressWarnings("deprecation") // mute warning for FrameworkInfo.setRole()
    private static void setRole(Protos.FrameworkInfo.Builder fwkInfoBuilder, String role) {
        fwkInfoBuilder.setRole(role);
    }
}
