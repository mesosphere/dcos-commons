package com.mesosphere.sdk.specification;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.curator.CuratorLocker;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.DcosCertInstaller;
import com.mesosphere.sdk.generated.SDKBuildInfo;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.*;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This class is a default implementation of the Service interface.  It serves mainly as an example
 * with hard-coded values for "user", and "master-uri", and failover timeouts.  More sophisticated
 * services may want to implement the Service interface directly.
 * <p>
 * Customizing the runtime user for individual tasks may be accomplished by customizing the 'user'
 * field on CommandInfo returned by {@link TaskSpec#getCommand()}.
 */
public class DefaultService implements Runnable {
    protected static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;
    protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultService.class);

    private DefaultScheduler.Builder schedulerBuilder;
    private AbstractScheduler scheduler;
    private StateStore stateStore;

    public DefaultService() {
        //No initialization needed
    }

    public DefaultService(File yamlSpecFile, SchedulerConfig schedulerConfig) throws Exception {
        this(yamlSpecFile, yamlSpecFile.getParentFile(), schedulerConfig);
    }

    public DefaultService(File yamlSpecFile, File configTemplateDir, SchedulerConfig schedulerConfig)
            throws Exception {
        this(RawServiceSpec.newBuilder(yamlSpecFile).build(), configTemplateDir, schedulerConfig);
    }

    public DefaultService(RawServiceSpec rawServiceSpec, File configTemplateDir, SchedulerConfig schedulerConfig)
            throws Exception {
        this(DefaultScheduler.newBuilder(
                DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerConfig, configTemplateDir).build(),
                schedulerConfig)
                .setPlansFrom(rawServiceSpec));
    }

    public DefaultService(
            ServiceSpec serviceSpecification,
            SchedulerConfig schedulerConfig,
            Collection<Plan> plans) throws Exception {
        this(DefaultScheduler.newBuilder(serviceSpecification, schedulerConfig)
                .setPlans(plans));
    }

    public DefaultService(DefaultScheduler.Builder schedulerBuilder) throws Exception {
        this.schedulerBuilder = schedulerBuilder;
        SchedulerConfig schedulerConfig = schedulerBuilder.getSchedulerConfig();
        LOGGER.info("Build information:\n- {}: {}, built {}\n- SDK: {}/{}, built {}",
                schedulerConfig.getPackageName(),
                schedulerConfig.getPackageVersion(),
                Instant.ofEpochMilli(schedulerConfig.getPackageBuildTimeMs()),
                SDKBuildInfo.VERSION,
                SDKBuildInfo.GIT_SHA,
                Instant.ofEpochMilli(SDKBuildInfo.BUILD_TIME_EPOCH_MS));
    }

    public static Boolean serviceSpecRequestsGpuResources(ServiceSpec serviceSpec) {
        boolean usesGpus = serviceSpec.getPods().stream()
                .flatMap(podSpec -> podSpec.getTasks().stream())
                .flatMap(taskSpec -> taskSpec.getResourceSet().getResources().stream())
                .anyMatch(resourceSpec -> resourceSpec.getName().equals("gpus")
                        && resourceSpec.getValue().getScalar().getValue() >= 1);
        // control automatic opt-in to scarce resources (GPUs) here. If the framework specifies GPU resources >= 1
        // then we opt-in to scarce resource, otherwise follow the default policy (which as of 8/3/17 was to opt-out)
        return usesGpus || DcosConstants.DEFAULT_GPU_POLICY;
    }

    private void initService() {
        // Use a single stateStore for either scheduler as the StateStoreCache requires a single instance of StateStore.
        this.stateStore = schedulerBuilder.getStateStore();
        if (schedulerBuilder.getSchedulerConfig().isUninstallEnabled()) {
            if (!StateStoreUtils.isUninstalling(stateStore)) {
                LOGGER.info("Service has been told to uninstall. Marking this in the persistent state store. " +
                        "Uninstall cannot be canceled once enabled.");
                StateStoreUtils.setUninstalling(stateStore);
            }

            LOGGER.info("Launching UninstallScheduler...");
            this.scheduler = new UninstallScheduler(
                    schedulerBuilder.getServiceSpec(),
                    stateStore,
                    schedulerBuilder.getConfigStore(),
                    schedulerBuilder.getSchedulerConfig());
        } else {
            if (StateStoreUtils.isUninstalling(stateStore)) {
                LOGGER.error("Service has been previously told to uninstall, this cannot be reversed. " +
                        "Reenable the uninstall flag to complete the process.");
                SchedulerUtils.hardExit(SchedulerErrorCode.SCHEDULER_ALREADY_UNINSTALLING);
            }
            this.scheduler = schedulerBuilder.build();
        }
        this.scheduler.start();
    }

    @Override
    public void run() {
        // Install the certs from "$MESOS_SANDBOX/.ssl" (if present) inside the JRE being used to run the scheduler.
        DcosCertInstaller.installCertificate(schedulerBuilder.getSchedulerConfig().getJavaHome());

        CuratorLocker locker = new CuratorLocker(schedulerBuilder.getServiceSpec());
        locker.lock();

        // Only create/start the scheduler (and state store, etc...) AFTER getting the curator lock above:
        initService();

        try {
            if (scheduler.getMesosScheduler().isPresent()) {
                Protos.Status status;
                Protos.FrameworkInfo frameworkInfo = getFrameworkInfo(schedulerBuilder.getServiceSpec(), stateStore);
                LOGGER.info("Registering framework: {}", TextFormat.shortDebugString(frameworkInfo));
                String zkUri =
                        String.format("zk://%s/mesos", schedulerBuilder.getServiceSpec().getZookeeperConnection());
                status = new SchedulerDriverFactory()
                        .create(
                                scheduler.getMesosScheduler().get(),
                                frameworkInfo,
                                zkUri,
                                schedulerBuilder.getSchedulerConfig())
                        .run();
                LOGGER.error("Scheduler driver exited with status: {}", status);
                // DRIVER_STOPPED will occur when we call stop(boolean) during uninstall.
                // When this happens, we continue running so that we can advertise that the uninstall plan is complete.
                if (status != null && status != Protos.Status.DRIVER_STOPPED) {
                    SchedulerUtils.hardExit(SchedulerErrorCode.DRIVER_EXITED);
                }
            }
        } finally {
            locker.unlock();
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

        if (serviceSpecRequestsGpuResources(serviceSpec)) {
            fwkInfoBuilder.addCapabilities(Protos.FrameworkInfo.Capability.newBuilder()
                    .setType(Protos.FrameworkInfo.Capability.Type.GPU_RESOURCES));
        }

        if (Capabilities.getInstance().supportsPreReservedResources()) {
            fwkInfoBuilder.addCapabilities(Protos.FrameworkInfo.Capability.newBuilder()
                    .setType(Protos.FrameworkInfo.Capability.Type.RESERVATION_REFINEMENT));
        }

        return fwkInfoBuilder.build();
    }

    @SuppressWarnings("deprecation") // for FrameworkInfo.setRole()
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
