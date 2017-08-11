package com.mesosphere.sdk.specification;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.curator.CuratorLocker;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.DcosCertInstaller;
import com.mesosphere.sdk.dcos.SecretsClient;
import com.mesosphere.sdk.dcos.auth.TokenProvider;
import com.mesosphere.sdk.dcos.http.DcosHttpClientBuilder;
import com.mesosphere.sdk.dcos.secrets.DefaultSecretsClient;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.evaluate.TLSEvaluationStage;
import com.mesosphere.sdk.scheduler.*;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.fluent.Executor;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
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
public class DefaultService implements Service {
    protected static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;
    protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultService.class);

    private DefaultScheduler.Builder schedulerBuilder;
    private Scheduler scheduler;
    private StateStore stateStore;

    public DefaultService() {
        //No initialization needed
    }

    public DefaultService(String yamlSpecification, SchedulerFlags schedulerFlags) throws Exception {
        this(RawServiceSpec.newBuilder(yamlSpecification).build(), schedulerFlags);
    }

    public DefaultService(File pathToYamlSpecification, SchedulerFlags schedulerFlags) throws Exception {
        this(RawServiceSpec.newBuilder(pathToYamlSpecification).build(), schedulerFlags);
    }

    public DefaultService(RawServiceSpec rawServiceSpec, SchedulerFlags schedulerFlags) throws Exception {
        this(DefaultScheduler.newBuilder(
                DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerFlags).build(), schedulerFlags)
                .setPlansFrom(rawServiceSpec));
    }

    public DefaultService(
            ServiceSpec serviceSpecification,
            SchedulerFlags schedulerFlags,
            Collection<Plan> plans) throws Exception {
        this(DefaultScheduler.newBuilder(serviceSpecification, schedulerFlags)
                .setPlans(plans));
    }

    public DefaultService(DefaultScheduler.Builder schedulerBuilder) throws Exception {
        this.schedulerBuilder = schedulerBuilder;
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
        if (schedulerBuilder.getSchedulerFlags().isUninstallEnabled()) {
            if (!StateStoreUtils.isUninstalling(stateStore)) {
                LOGGER.info("Service has been told to uninstall. Marking this in the persistent state store. " +
                        "Uninstall cannot be canceled once enabled.");
                StateStoreUtils.setUninstalling(stateStore);
            }

            Optional<SecretsClient> secretsClient = Optional.empty();
            if (!TaskUtils.getTasksWithTLS(getServiceSpec()).isEmpty()) {
                try {
                    TokenProvider tokenProvider = TLSEvaluationStage.Builder.tokenProviderFromEnvironment(
                            schedulerBuilder.getSchedulerFlags());
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

            LOGGER.info("Launching UninstallScheduler...");
            this.scheduler = new UninstallScheduler(
                    schedulerBuilder.getServiceSpec().getName(),
                    schedulerBuilder.getSchedulerFlags().getApiServerPort(),
                    schedulerBuilder.getSchedulerFlags().getApiServerInitTimeout(),
                    stateStore,
                    schedulerBuilder.getConfigStore(),
                    schedulerBuilder.getSchedulerFlags(),
                    secretsClient);
        } else {
            if (StateStoreUtils.isUninstalling(stateStore)) {
                LOGGER.error("Service has been previously told to uninstall, this cannot be reversed. " +
                        "Reenable the uninstall flag to complete the process.");
                SchedulerUtils.hardExit(SchedulerErrorCode.SCHEDULER_ALREADY_UNINSTALLING);
            }
            this.scheduler = schedulerBuilder.build();
        }
    }

    @Override
    public void run() {
        // Install the certs from "$MESOS_SANDBOX/.ssl" (if present) inside the JRE being used to run the scheduler.
        DcosCertInstaller.installCertificate(schedulerBuilder.getSchedulerFlags().getJavaHome());

        initService();

        CuratorLocker locker = new CuratorLocker(schedulerBuilder.getServiceSpec());
        locker.lock();
        try {
            register();
        } finally {
            locker.unlock();
        }
    }

    /**
     * Creates and registers the service with Mesos, while starting a Jetty HTTP API service on the {@code apiPort}.
     */
    @Override
    public void register() {
        if (allButStateStoreUninstalled()) {
            LOGGER.info("Not registering framework because it is uninstalling.");
            return;
        }
        Protos.FrameworkInfo frameworkInfo = getFrameworkInfo(schedulerBuilder.getServiceSpec(), stateStore);
        LOGGER.info("Registering framework: {}", TextFormat.shortDebugString(frameworkInfo));
        String zkUri = String.format("zk://%s/mesos", schedulerBuilder.getServiceSpec().getZookeeperConnection());
        Protos.Status status = new SchedulerDriverFactory()
                .create(scheduler, frameworkInfo, zkUri, schedulerBuilder.getSchedulerFlags())
                .run();
        // TODO(nickbp): Exit scheduler process here?
        LOGGER.error("Scheduler driver exited with status: {}", status);
    }

    private boolean allButStateStoreUninstalled() {
        // Because we cannot delete the root ZK node (ACLs on the master, see StateStore.clearAllData() for more
        // details) we have to clear everything under it. This results in a race condition, where DefaultService can
        // have register() called after the StateStore already has the uninstall bit wiped.
        //
        // As can be seen in DefaultService.initService(), DefaultService.register() will only be called in uninstall
        // mode if schedulerFlags.isUninstallEnabled() == true. Therefore we can use it as an OR along with
        // StateStoreUtils.isUninstalling().

        // resources are destroyed and unreserved, framework ID is gone, but tasks still need to be cleared
        return isUninstalling() && !stateStore.fetchFrameworkId().isPresent() && tasksNeedClearing();
    }

    private boolean tasksNeedClearing() {
        return ResourceUtils.getResourceIds(
                ResourceUtils.getAllResources(stateStore.fetchTasks())).stream()
                .allMatch(resourceId -> resourceId.startsWith(Constants.TOMBSTONE_MARKER));
    }

    private boolean isUninstalling() {
        return StateStoreUtils.isUninstalling(stateStore) || schedulerBuilder.getSchedulerFlags().isUninstallEnabled();
    }

    protected ServiceSpec getServiceSpec() {
        return this.schedulerBuilder.getServiceSpec();
    }

    private Protos.FrameworkInfo getFrameworkInfo(ServiceSpec serviceSpec, StateStore stateStore) {
        return getFrameworkInfo(serviceSpec, stateStore, serviceSpec.getUser(), TWO_WEEK_SEC);
    }

    protected Protos.FrameworkInfo getFrameworkInfo(
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

        List<String> roles = getRoles(serviceSpec);
        if (roles.size() == 1) {
            fwkInfoBuilder.setRole(roles.get(0));
        } else {
            fwkInfoBuilder.addCapabilities(Protos.FrameworkInfo.Capability.newBuilder()
                    .setType(Protos.FrameworkInfo.Capability.Type.MULTI_ROLE));
            fwkInfoBuilder.addAllRoles(getRoles(serviceSpec));
        }

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

    private List<String> getRoles(ServiceSpec serviceSpec) {
        List<String> roles = new ArrayList<>();
        roles.add(serviceSpec.getRole());
        roles.addAll(
                serviceSpec.getPods().stream()
                .filter(podSpec -> !podSpec.getPreReservedRole().equals(Constants.ANY_ROLE))
                .map(podSpec -> podSpec.getPreReservedRole() + "/" + serviceSpec.getRole())
                .collect(Collectors.toList()));

        return roles;
    }
}
