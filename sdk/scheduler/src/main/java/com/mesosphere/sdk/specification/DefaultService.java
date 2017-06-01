package com.mesosphere.sdk.specification;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.curator.CuratorLocker;
import com.mesosphere.sdk.dcos.DcosCertInstaller;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.ResourceCollectionUtils;
import com.mesosphere.sdk.scheduler.*;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.Optional;

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
    protected static final String USER = "root";
    protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultService.class);

    private DefaultScheduler.Builder schedulerBuilder;
    private Scheduler scheduler;
    private StateStore stateStore;

    public DefaultService() {
        //No initialization needed
    }

    public DefaultService(String yamlSpecification, SchedulerFlags schedulerFlags) throws Exception {
        this(YAMLServiceSpecFactory.generateRawSpecFromYAML(yamlSpecification), schedulerFlags);
    }

    public DefaultService(File pathToYamlSpecification, SchedulerFlags schedulerFlags) throws Exception {
        this(YAMLServiceSpecFactory.generateRawSpecFromYAML(pathToYamlSpecification), schedulerFlags);
    }

    public DefaultService(RawServiceSpec rawServiceSpec, SchedulerFlags schedulerFlags) throws Exception {
        this(DefaultScheduler.newBuilder(
                YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, schedulerFlags), schedulerFlags)
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

    private void initService() {

        // Use a single stateStore for either scheduler as the StateStoreCache requires a single instance of StateStore.
        this.stateStore = schedulerBuilder.getStateStore();
        if (schedulerBuilder.getSchedulerFlags().isUninstallEnabled()) {
            if (!StateStoreUtils.isUninstalling(stateStore)) {
                LOGGER.info("Service has been told to uninstall. Marking this in the persistent state store. " +
                        "Uninstall cannot be canceled once enabled.");
                StateStoreUtils.setUninstalling(stateStore);
            }

            LOGGER.info("Launching UninstallScheduler...");
            this.scheduler = new UninstallScheduler(
                    schedulerBuilder.getServiceSpec().getApiPort(),
                    schedulerBuilder.getSchedulerFlags().getApiServerInitTimeout(),
                    stateStore,
                    schedulerBuilder.getConfigStore());
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
        return ResourceCollectionUtils.getResourceIds(
                ResourceCollectionUtils.getAllResources(stateStore.fetchTasks())).stream()
                .allMatch(resourceId -> resourceId.startsWith(Constants.TOMBSTONE_MARKER));
    }

    private boolean isUninstalling() {
        return StateStoreUtils.isUninstalling(stateStore) || schedulerBuilder.getSchedulerFlags().isUninstallEnabled();
    }

    protected ServiceSpec getServiceSpec() {
        return this.schedulerBuilder.getServiceSpec();
    }

    public static Boolean serviceSpecRequestsGpuResources(ServiceSpec serviceSpec) {
        Collection<PodSpec> pods = serviceSpec.getPods();
        for (PodSpec pod : pods) {
            for (TaskSpec taskSpec : pod.getTasks()) {
                for (ResourceSpec resourceSpec : taskSpec.getResourceSet().getResources()) {
                    if (resourceSpec.getName().equals("gpus") && resourceSpec.getValue().getScalar().getValue() >= 1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Protos.FrameworkInfo getFrameworkInfo(ServiceSpec serviceSpec, StateStore stateStore) {
        return getFrameworkInfo(serviceSpec, stateStore, USER, TWO_WEEK_SEC);
    }

    protected Protos.FrameworkInfo getFrameworkInfo(
            ServiceSpec serviceSpec,
            StateStore stateStore,
            String userString,
            int failoverTimeoutSec) {
        final String serviceName = serviceSpec.getName();

        Protos.FrameworkInfo.Builder fwkInfoBuilder = Protos.FrameworkInfo.newBuilder()
                .setName(serviceName)
                .setFailoverTimeout(failoverTimeoutSec)
                .setUser(userString)
                .setCheckpoint(true);

        // Use provided role if specified, otherwise default to "<svcname>-role".
        //TODO(nickbp): Use fwkInfoBuilder.addRoles(role) AND fwkInfoBuilder.addCapabilities(MULTI_ROLE)
        if (StringUtils.isEmpty(serviceSpec.getRole())) {
            fwkInfoBuilder.setRole(SchedulerUtils.nameToRole(serviceName));
        } else {
            fwkInfoBuilder.setRole(serviceSpec.getRole());
        }

        // Use provided principal if specified, otherwise default to "<svcname>-principal".
        if (StringUtils.isEmpty(serviceSpec.getPrincipal())) {
            fwkInfoBuilder.setPrincipal(SchedulerUtils.nameToPrincipal(serviceName));
        } else {
            fwkInfoBuilder.setPrincipal(serviceSpec.getPrincipal());
        }

        if (!StringUtils.isEmpty(serviceSpec.getWebUrl())) {
            fwkInfoBuilder.setWebuiUrl(serviceSpec.getWebUrl());
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

        return fwkInfoBuilder.build();
    }
}
