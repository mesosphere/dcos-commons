package com.mesosphere.sdk.scheduler;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.curator.CuratorLocker;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.DcosCertInstaller;
import com.mesosphere.sdk.dcos.SecretsClient;
import com.mesosphere.sdk.dcos.auth.TokenProvider;
import com.mesosphere.sdk.dcos.http.DcosHttpClientBuilder;
import com.mesosphere.sdk.dcos.secrets.DefaultSecretsClient;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.evaluate.TLSEvaluationStage;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.validation.CapabilityValidator;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.PersisterException;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.fluent.Executor;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Class which sets up and executes the correct {@link AbstractScheduler} instance.
 */
public class DefaultService implements Runnable {
    private static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultService.class);

    private final SchedulerBuilder schedulerBuilder;

    public static DefaultService fromRawServiceSpec(
            RawServiceSpec rawServiceSpec, SchedulerFlags schedulerFlags) throws Exception {
        return new DefaultService(DefaultScheduler.newBuilder(
                DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerFlags).build(), schedulerFlags)
                .setPlansFrom(rawServiceSpec));
    }

    public static DefaultService fromServiceSpec(
            ServiceSpec serviceSpecification, SchedulerFlags schedulerFlags, Collection<Plan> plans)
                    throws PersisterException {
        return new DefaultService(DefaultScheduler.newBuilder(serviceSpecification, schedulerFlags).setPlans(plans));
    }

    public static DefaultService fromSchedulerBuilder(SchedulerBuilder schedulerBuilder) {
        return new DefaultService(schedulerBuilder);
    }

    private DefaultService(SchedulerBuilder schedulerBuilder) {
        this.schedulerBuilder = schedulerBuilder;
    }

    @Override
    public void run() {
        // Use a single stateStore for either scheduler as the StateStoreCache requires a single instance of StateStore.
        final StateStore stateStore = schedulerBuilder.getStateStore();
        final Scheduler mesosScheduler;
        if (schedulerBuilder.getSchedulerFlags().isUninstallEnabled()) {
            if (!StateStoreUtils.isUninstalling(stateStore)) {
                LOGGER.info("Service has been told to uninstall. Marking this in the persistent state store. " +
                        "Uninstall cannot be canceled once enabled.");
                StateStoreUtils.setUninstalling(stateStore);
            }

            mesosScheduler = getUninstallScheduler(
                    stateStore,
                    schedulerBuilder.getConfigStore(),
                    schedulerBuilder.getServiceSpec(),
                    schedulerBuilder.getSchedulerFlags());
        } else {
            if (StateStoreUtils.isUninstalling(stateStore)) {
                LOGGER.error("Service has been previously told to uninstall, this cannot be reversed. " +
                        "Reenable the uninstall flag to complete the process.");
                SchedulerUtils.hardExit(SchedulerErrorCode.SCHEDULER_ALREADY_UNINSTALLING);
            }

            mesosScheduler = schedulerBuilder.build();
        }

        CuratorLocker locker = new CuratorLocker(schedulerBuilder.getServiceSpec());
        locker.lock();
        try {
            runService(mesosScheduler, schedulerBuilder.getServiceSpec(), schedulerBuilder.getSchedulerFlags(), stateStore);
        } finally {
            locker.unlock();
        }
    }

    private static Scheduler getUninstallScheduler(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            ServiceSpec serviceSpec,
            SchedulerFlags schedulerFlags) {
        Optional<SecretsClient> secretsClient = Optional.empty();
        if (!TaskUtils.getTasksWithTLS(serviceSpec).isEmpty()) {
            try {
                TokenProvider tokenProvider = TLSEvaluationStage.Builder.tokenProviderFromEnvironment(schedulerFlags);
                Executor executor = Executor.newInstance(
                        new DcosHttpClientBuilder()
                                .setTokenProvider(tokenProvider)
                                .setRedirectStrategy(new LaxRedirectStrategy())
                                .build());
                secretsClient = Optional.of(new DefaultSecretsClient(executor));
            } catch (Exception e) {
                LOGGER.error("Failed to create a secrets store client, " +
                        "TLS artifacts possibly won't be cleaned up from secrets store", e);
            }
        }

        return new UninstallScheduler(serviceSpec.getName(), stateStore, configStore, schedulerFlags, secretsClient);
    }

    private static void runService(
            Scheduler mesosScheduler,
            ServiceSpec serviceSpec,
            SchedulerFlags schedulerFlags,
            StateStore stateStore) {
        // Install the certs from "$MESOS_SANDBOX/.ssl" (if present) inside the JRE being used to run the scheduler.
        DcosCertInstaller.installCertificate(schedulerFlags.getJavaHome());

        if (allButStateStoreUninstalled(stateStore, schedulerFlags)) {
            LOGGER.info("Not registering framework because it is uninstalling.");
            return;
        }
        Protos.FrameworkInfo frameworkInfo = getFrameworkInfo(serviceSpec, stateStore);
        LOGGER.info("Registering framework: {}", TextFormat.shortDebugString(frameworkInfo));
        String zkUri = String.format("zk://%s/mesos", serviceSpec.getZookeeperConnection());
        Protos.Status status = new SchedulerDriverFactory()
                .create(mesosScheduler, frameworkInfo, zkUri, schedulerFlags)
                .run();
        LOGGER.error("Scheduler driver exited with status: {}", status);
        // DRIVER_STOPPED will occur when we call stop(boolean) during uninstall.
        // When this happens, we want to continue running so that we can advertise that the uninstall plan is complete.
        if (status != Protos.Status.DRIVER_STOPPED) {
            SchedulerUtils.hardExit(SchedulerErrorCode.DRIVER_EXITED);
        }
    }

    private static boolean allButStateStoreUninstalled(StateStore stateStore, SchedulerFlags schedulerFlags) {
        // Because we cannot delete the root ZK node (ACLs on the master, see StateStore.clearAllData() for more
        // details) we have to clear everything under it. This results in a race condition, where DefaultService can
        // have register() called after the StateStore already has the uninstall bit wiped.
        //
        // As can be seen in DefaultService.initService(), DefaultService.register() will only be called in uninstall
        // mode if schedulerFlags.isUninstallEnabled() == true. Therefore we can use it as an OR along with
        // StateStoreUtils.isUninstalling().

        // resources are destroyed and unreserved, framework ID is gone, but tasks still need to be cleared
        return isUninstalling(stateStore, schedulerFlags) &&
                !stateStore.fetchFrameworkId().isPresent() &&
                tasksNeedClearing(stateStore);
    }

    private static boolean tasksNeedClearing(StateStore stateStore) {
        return ResourceUtils.getResourceIds(
                ResourceUtils.getAllResources(stateStore.fetchTasks())).stream()
                .allMatch(resourceId -> resourceId.startsWith(Constants.TOMBSTONE_MARKER));
    }

    private static boolean isUninstalling(StateStore stateStore, SchedulerFlags schedulerFlags) {
        return StateStoreUtils.isUninstalling(stateStore) || schedulerFlags.isUninstallEnabled();
    }

    private static Protos.FrameworkInfo getFrameworkInfo(ServiceSpec serviceSpec, StateStore stateStore) {
        return getFrameworkInfo(serviceSpec, stateStore, serviceSpec.getUser(), TWO_WEEK_SEC);
    }

    private static Protos.FrameworkInfo getFrameworkInfo(
            ServiceSpec serviceSpec,
            StateStore stateStore,
            String userString,
            int failoverTimeoutSec) {

        Protos.FrameworkInfo.Builder fwkInfoBuilder = Protos.FrameworkInfo.newBuilder()
                .setName(serviceSpec.getName())
                .setPrincipal(serviceSpec.getPrincipal())
                .setFailoverTimeout(failoverTimeoutSec)
                .setUser(userString)
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
