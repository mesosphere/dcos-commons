package com.mesosphere.sdk.scheduler;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.curator.CuratorLocker;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.DcosCertInstaller;
import com.mesosphere.sdk.generated.SDKBuildInfo;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.validation.CapabilityValidator;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.PersisterException;

import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Class which sets up and executes the correct {@link AbstractScheduler} instance.
 */
public class SchedulerRunner implements Runnable {
    private static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerRunner.class);

    private final SchedulerBuilder schedulerBuilder;

    public static SchedulerRunner fromRawServiceSpec(
            RawServiceSpec rawServiceSpec, SchedulerConfig schedulerConfig, File configTemplateDir) throws Exception {
        return fromSchedulerBuilder(DefaultScheduler.newBuilder(
                DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerConfig, configTemplateDir).build(),
                schedulerConfig)
                .setPlansFrom(rawServiceSpec));
    }

    public static SchedulerRunner fromServiceSpec(
            ServiceSpec serviceSpecification, SchedulerConfig schedulerConfig, Collection<Plan> plans)
                    throws PersisterException {
        return fromSchedulerBuilder(DefaultScheduler.newBuilder(serviceSpecification, schedulerConfig).setPlans(plans));
    }

    public static SchedulerRunner fromSchedulerBuilder(SchedulerBuilder schedulerBuilder) {
        return new SchedulerRunner(schedulerBuilder);
    }

    private SchedulerRunner(SchedulerBuilder schedulerBuilder) {
        this.schedulerBuilder = schedulerBuilder;
        SchedulerConfig flags = schedulerBuilder.getSchedulerConfig();
        LOGGER.info("Build information:\n- {}: {}, built {}\n- SDK: {}/{}, built {}",
                flags.getPackageName(), flags.getPackageVersion(), Instant.ofEpochMilli(flags.getPackageBuildTimeMs()),
                SDKBuildInfo.VERSION, SDKBuildInfo.GIT_SHA, Instant.ofEpochMilli(SDKBuildInfo.BUILD_TIME_EPOCH_MS));
    }

    @Override
    public void run() {
        CuratorLocker locker = new CuratorLocker(schedulerBuilder.getServiceSpec());
        locker.lock();
        try {
            AbstractScheduler scheduler = schedulerBuilder.build();
            scheduler.start();
            Optional<Scheduler> mesosScheduler = scheduler.getMesosScheduler();
            if (mesosScheduler.isPresent()) {
                runScheduler(
                        mesosScheduler.get(),
                        schedulerBuilder.getServiceSpec(),
                        schedulerBuilder.getSchedulerConfig(),
                        schedulerBuilder.getStateStore());
            }
        } finally {
            locker.unlock();
        }
    }

    private static void runScheduler(
            Scheduler mesosScheduler, ServiceSpec serviceSpec, SchedulerConfig schedulerConfig, StateStore stateStore) {
        // Install the certs from "$MESOS_SANDBOX/.ssl" (if present) inside the JRE being used to run the scheduler.
        DcosCertInstaller.installCertificate(schedulerConfig.getJavaHome());

        Protos.FrameworkInfo frameworkInfo = getFrameworkInfo(serviceSpec, stateStore);
        LOGGER.info("Registering framework: {}", TextFormat.shortDebugString(frameworkInfo));
        String zkUri = String.format("zk://%s/mesos", serviceSpec.getZookeeperConnection());
        Protos.Status status = new SchedulerDriverFactory()
                .create(mesosScheduler, frameworkInfo, zkUri, schedulerConfig)
                .run();
        LOGGER.error("Scheduler driver exited with status: {}", status);
        // DRIVER_STOPPED will occur when we call stop(boolean) during uninstall.
        // When this happens, we want to continue running so that we can advertise that the uninstall plan is complete.
        if (status != Protos.Status.DRIVER_STOPPED) {
            SchedulerUtils.hardExit(SchedulerErrorCode.DRIVER_EXITED);
        }
    }

    private static Protos.FrameworkInfo getFrameworkInfo(ServiceSpec serviceSpec, StateStore stateStore) {
        Protos.FrameworkInfo.Builder fwkInfoBuilder = Protos.FrameworkInfo.newBuilder()
                .setName(serviceSpec.getName())
                .setPrincipal(serviceSpec.getPrincipal())
                .setFailoverTimeout(TWO_WEEK_SEC)
                .setUser(serviceSpec.getUser())
                .setCheckpoint(true);

        setRoles(fwkInfoBuilder, serviceSpec);

        // The framework ID is not available when we're being started for the first time.
        Optional<Protos.FrameworkID> optionalFrameworkId = stateStore.fetchFrameworkId();
        optionalFrameworkId.ifPresent(fwkInfoBuilder::setId);

        if (!StringUtils.isEmpty(serviceSpec.getWebUrl())) {
            fwkInfoBuilder.setWebuiUrl(serviceSpec.getWebUrl());
        }

        if (Capabilities.getInstance().supportsGpuResource()
                && CapabilityValidator.serviceSpecRequestsGpuResources(serviceSpec)) {
            fwkInfoBuilder.addCapabilities(Protos.FrameworkInfo.Capability.newBuilder()
                    .setType(Protos.FrameworkInfo.Capability.Type.GPU_RESOURCES));
        }

        if (Capabilities.getInstance().supportsPreReservedResources()) {
            fwkInfoBuilder.addCapabilities(Protos.FrameworkInfo.Capability.newBuilder()
                    .setType(Protos.FrameworkInfo.Capability.Type.RESERVATION_REFINEMENT));
        }

        return fwkInfoBuilder.build();
    }

    @SuppressWarnings("deprecation") // mute warning for FrameworkInfo.setRole()
    private static void setRoles(Protos.FrameworkInfo.Builder fwkInfoBuilder, ServiceSpec serviceSpec) {
        List<String> preReservedRoles =
                serviceSpec.getPods().stream()
                .filter(podSpec -> !podSpec.getPreReservedRole().equals(Constants.ANY_ROLE))
                .map(podSpec -> podSpec.getPreReservedRole() + "/" + serviceSpec.getRole())
                .collect(Collectors.toList());
        if (preReservedRoles.isEmpty()) {
            fwkInfoBuilder.setRole(serviceSpec.getRole());
        } else {
            fwkInfoBuilder.addCapabilities(Protos.FrameworkInfo.Capability.newBuilder()
                    .setType(Protos.FrameworkInfo.Capability.Type.MULTI_ROLE));
            fwkInfoBuilder.addRoles(serviceSpec.getRole());
            fwkInfoBuilder.addAllRoles(preReservedRoles);
        }
    }
}
