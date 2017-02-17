package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.curator.CuratorUtils;
import com.mesosphere.sdk.dcos.DcosCertInstaller;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerDriverFactory;
import com.mesosphere.sdk.scheduler.SchedulerErrorCode;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.scheduler.plan.Plan;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.api.JettyApiServer;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;

import org.eclipse.jetty.util.ArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
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
    protected static final String LOCK_PATH = "lock";
    protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultService.class);

    private DefaultScheduler.Builder schedulerBuilder;

    private ServiceSpec serviceSpec;

    public DefaultService(String yamlSpecification) throws Exception {
        this(YAMLServiceSpecFactory.generateRawSpecFromYAML(yamlSpecification));
    }

    public DefaultService(File pathToYamlSpecification) throws Exception {
        this(YAMLServiceSpecFactory.generateRawSpecFromYAML(pathToYamlSpecification));
    }

    public DefaultService(RawServiceSpec rawServiceSpec) throws Exception {
        this(DefaultScheduler.newBuilder(YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec))
                .setPlansFrom(rawServiceSpec));
    }

    public DefaultService(ServiceSpec serviceSpecification, Collection<Plan> plans) throws Exception {
        this(DefaultScheduler.newBuilder(serviceSpecification).setPlans(plans));
    }

    public DefaultService(DefaultScheduler.Builder schedulerBuilder) throws Exception {
        this.schedulerBuilder = schedulerBuilder;
        this.serviceSpec = schedulerBuilder.getServiceSpec();

        // Install the certs from "$MESOS_SANDBOX/.ssl" (if present) inside the JRE being used to run the scheduler.
        DcosCertInstaller.installCertificate(System.getenv("JAVA_HOME"));

        CuratorFramework curatorClient = CuratorFrameworkFactory.newClient(
                schedulerBuilder.getServiceSpec().getZookeeperConnection(), CuratorUtils.getDefaultRetry());
        curatorClient.start();

        InterProcessMutex curatorMutex = lock(curatorClient, schedulerBuilder.getServiceSpec().getName());
        try {
            register();
        } finally {
            unlock(curatorMutex);
            curatorClient.close();
        }
    }

    public DefaultService(){
    }

    /**
     * Gets an exclusive lock on service-specific ZK node to ensure two schedulers aren't running simultaneously for the
     * same service.
     */
    private static InterProcessMutex lock(CuratorFramework curatorClient, String serviceName) {
        return lock(curatorClient, serviceName, LOCK_PATH, LOCK_ATTEMPTS);
    }

    protected static InterProcessMutex lock(
            CuratorFramework curatorClient,
            String serviceName,
            String lockPathString,
            int lockAttempts) {
        String rootPath = CuratorUtils.toServiceRootPath(serviceName);
        String lockPath = CuratorUtils.join(rootPath, lockPathString);
        InterProcessMutex curatorMutex = new InterProcessMutex(curatorClient, lockPath);

        LOGGER.info("Acquiring ZK lock on {}...", lockPath);
        final String failureLogMsg = String.format("Failed to acquire ZK lock on %s. " +
                "Duplicate service named '%s', or recently restarted instance of '%s'?",
                lockPath, serviceName, serviceName);
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
        DefaultScheduler defaultScheduler = schedulerBuilder.build();
        ServiceSpec serviceSpec = schedulerBuilder.getServiceSpec();

        startApiServer(defaultScheduler, serviceSpec.getApiPort());
        registerAndRunFramework(
                defaultScheduler,
                getFrameworkInfo(serviceSpec, schedulerBuilder.getStateStore()),
                serviceSpec.getZookeeperConnection());
    }

    private void startApiServer(DefaultScheduler defaultScheduler, int apiPort) {
        startApiServer(defaultScheduler, apiPort, Collections.emptyList());
    }

    protected ServiceSpec getServiceSpec() {
        return this.serviceSpec;
    }

    protected void startApiServer(
            DefaultScheduler defaultScheduler,
            int apiPort,
            Collection<Object> additionalResources) {
        Collection<Object> resourceList = new ArrayQueue<>();
        new Thread(new Runnable() {
            @Override
            public void run() {
                JettyApiServer apiServer = null;
                try {
                    LOGGER.info("Starting API server.");
                    resourceList.addAll(defaultScheduler.getResources());
                    resourceList.addAll(additionalResources);
                    apiServer = new JettyApiServer(apiPort, resourceList);
                    apiServer.start();
                } catch (Exception e) {
                    LOGGER.error("API Server failed with exception: ", e);
                } finally {
                    LOGGER.info("API Server exiting.");
                    try {
                        if (apiServer != null) {
                            apiServer.stop();
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to stop API server with exception: ", e);
                    }
                }
            }
        }).start();
    }

    private static void registerAndRunFramework(
            Scheduler sched,
            Protos.FrameworkInfo frameworkInfo,
            String zookeeperHost) {
        LOGGER.info("Registering framework: {}", TextFormat.shortDebugString(frameworkInfo));
        new SchedulerDriverFactory().create(sched, frameworkInfo, String.format("zk://%s/mesos", zookeeperHost)).run();
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
        if (optionalFrameworkId.isPresent()) {
            fwkInfoBuilder.setId(optionalFrameworkId.get());
        }

        if (!StringUtils.isEmpty(serviceSpec.getWebUrl())) {
            fwkInfoBuilder.setWebuiUrl(serviceSpec.getWebUrl());
        }

        return fwkInfoBuilder.build();
    }
}
