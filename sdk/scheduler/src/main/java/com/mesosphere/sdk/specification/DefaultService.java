package com.mesosphere.sdk.specification;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.curator.CuratorUtils;
import com.mesosphere.sdk.dcos.DcosCertInstaller;
import com.mesosphere.sdk.scheduler.*;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
    protected static final int LOCK_ATTEMPTS = 3;
    protected static final String USER = "root";
    protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultService.class);

    private Scheduler scheduler;
    private ServiceSpec serviceSpec;
    private StateStore stateStore;
    private SchedulerFlags schedulerFlags;

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
        initService(schedulerBuilder);
    }

    protected void initService(DefaultScheduler.Builder schedulerBuilder) throws Exception {
        this.serviceSpec = schedulerBuilder.getServiceSpec();
        this.schedulerFlags = schedulerBuilder.getSchedulerFlags();

        try {
            // Use a single stateStore for either scheduler as the StateStoreCache
            // requires a single instance of StateStore.
            this.stateStore = schedulerBuilder.getStateStore();
            if (schedulerBuilder.getSchedulerFlags().isUninstallEnabled()) {
                if (!StateStoreUtils.isUninstalling(stateStore)) {
                    LOGGER.info("Service has been told to uninstall. Marking this in the persistent state store. " +
                            "Uninstall cannot be canceled once enabled.");
                    StateStoreUtils.setUninstalling(stateStore, true);
                }

                ConfigStore<ServiceSpec> configStore = DefaultScheduler.createConfigStore(serviceSpec);
                LOGGER.info("Launching UninstallScheduler...");
                this.scheduler = new UninstallScheduler(
                        schedulerBuilder.getServiceSpec().getApiPort(),
                        schedulerBuilder.getSchedulerFlags().getApiServerInitTimeout(),
                        stateStore,
                        configStore);
            } else {
                if (StateStoreUtils.isUninstalling(stateStore)) {
                    LOGGER.error("Service has been previously told to uninstall, this cannot be reversed. " +
                            "Reenable the uninstall flag to complete the process.");
                    SchedulerUtils.hardExit(SchedulerErrorCode.SCHEDULER_ALREADY_UNINSTALLING);
                }
                this.scheduler = schedulerBuilder.build();
            }
        } catch (Throwable e) {
            LOGGER.error("Failed to build scheduler.", e);
            SchedulerUtils.hardExit(SchedulerErrorCode.SCHEDULER_BUILD_FAILED);
        }
    }

    @Override
    public void run() {
        // Install the certs from "$MESOS_SANDBOX/.ssl" (if present) inside the JRE being used to run the scheduler.
        DcosCertInstaller.installCertificate(schedulerFlags.getJavaHome());

        CuratorFramework curatorClient = CuratorFrameworkFactory.newClient(
                serviceSpec.getZookeeperConnection(), CuratorUtils.getDefaultRetry());
        curatorClient.start();

        InterProcessMutex curatorMutex = lock(curatorClient, serviceSpec.getName(), LOCK_ATTEMPTS);
        try {
            register();
        } finally {
            unlock(curatorMutex);
            curatorClient.close();
        }
    }

    /**
     * Gets an exclusive lock on service-specific ZK node to ensure two schedulers aren't running simultaneously for the
     * same service.
     */
    protected static InterProcessMutex lock(CuratorFramework curatorClient, String serviceName, int lockAttempts) {
        String lockPath = CuratorUtils.join(
                CuratorUtils.toServiceRootPath(serviceName), CuratorStateStore.LOCK_PATH_NAME);
        InterProcessMutex curatorMutex = new InterProcessMutex(curatorClient, lockPath);

        LOGGER.info("Acquiring ZK lock on {}...", lockPath);
        String format = "Failed to acquire ZK lock on %s. " +
                "Duplicate service named '%s', or recently restarted instance of '%s'?";
        final String failureLogMsg = String.format(format, lockPath, serviceName, serviceName);
        try {
            for (int i = 0; i < lockAttempts; ++i) {
                if (curatorMutex.acquire(10, TimeUnit.SECONDS)) {
                    return curatorMutex;
                }
                LOGGER.error("{}/{} {} Retrying lock...", i + 1, lockAttempts, failureLogMsg);
            }
            LOGGER.error(failureLogMsg + " Restarting scheduler process to try again.");
            SchedulerUtils.hardExit(SchedulerErrorCode.LOCK_UNAVAILABLE);
        } catch (Exception ex) {
            LOGGER.error(String.format("Error acquiring ZK lock on path: %s", lockPath), ex);
            SchedulerUtils.hardExit(SchedulerErrorCode.LOCK_UNAVAILABLE);
        }
        return null; // not reachable, only here for a happy java
    }

    /**
     * Releases the lock previously obtained by {@link #lock(CuratorFramework, String)}.
     */
    protected static void unlock(InterProcessMutex curatorMutex) {
        try {
            curatorMutex.release();
        } catch (Exception ex) {
            LOGGER.error("Error releasing ZK lock.", ex);
        }
    }

    /**
     * Creates and registers the service with Mesos, while starting a Jetty HTTP API service on the {@code apiPort}.
     */
    @Override
    public void register() {
        Protos.FrameworkInfo frameworkInfo = getFrameworkInfo(serviceSpec, stateStore);
        LOGGER.info("Registering framework: {}", TextFormat.shortDebugString(frameworkInfo));
        String zkUri = String.format("zk://%s/mesos", serviceSpec.getZookeeperConnection());
        Protos.Status status = new SchedulerDriverFactory().create(scheduler, frameworkInfo, zkUri, schedulerFlags).
                run();
        // TODO(nickbp): Exit scheduler process here?
        LOGGER.error("Scheduler driver exited with status: {}", status);
    }

    protected ServiceSpec getServiceSpec() {
        return this.serviceSpec;
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
