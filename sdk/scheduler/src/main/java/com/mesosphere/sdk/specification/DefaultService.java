package com.mesosphere.sdk.specification;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.curator.CuratorUtils;
import com.mesosphere.sdk.scheduler.SchedulerErrorCode;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import com.mesosphere.sdk.api.JettyApiServer;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.config.ConfigurationUpdater;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.DcosCluster;
import com.mesosphere.sdk.offer.OfferRequirementProvider;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerDriverFactory;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.specification.validation.CapabilityValidator;
import com.mesosphere.sdk.specification.yaml.RawPlan;
import com.mesosphere.sdk.specification.yaml.RawServiceSpecification;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;
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
    protected static final String USER = "root";
    protected static final String LOCK_PATH = "lock";
    protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultService.class);

    protected int apiPort;
    protected String zkConnectionString;

    protected InterProcessMutex curatorMutex;
    protected CuratorFramework curatorClient;
    protected StateStore stateStore;
    protected ServiceSpec serviceSpec;
    protected Collection<Plan> plans;
    protected ConfigStore<ServiceSpec> configTargetStore;
    protected UUID targetConfigId;
    protected OfferRequirementProvider offerRequirementProvider;

    public DefaultService() {
    }

    public DefaultService(String yamlSpecification) throws Exception {
        this(YAMLServiceSpecFactory.generateRawSpecFromYAML(yamlSpecification));
    }

    public DefaultService(File pathToYamlSpecification) throws Exception {
        this(YAMLServiceSpecFactory.generateRawSpecFromYAML(pathToYamlSpecification));
    }

    public DefaultService(RawServiceSpecification rawServiceSpecification) throws Exception {
        CapabilityValidator capabilityValidator = new CapabilityValidator(new Capabilities(new DcosCluster()));
        this.serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpecification);
        capabilityValidator.validate(this.serviceSpec);

        init();
        this.plans = generatePlansFromRawSpec(rawServiceSpecification);
        register(serviceSpec, this.plans);
        unlock();
    }

    public DefaultService(ServiceSpec serviceSpecification) {
        this(serviceSpecification, Collections.emptyList());
    }

    public DefaultService(ServiceSpec serviceSpec, Collection<Plan> plans) {
        this.serviceSpec = serviceSpec;
        init();
        this.plans = plans;
        register(serviceSpec, this.plans);
    }

    private void lock() {
        String rootPath = CuratorUtils.toServiceRootPath(serviceSpec.getName());
        String lockPath = CuratorUtils.join(rootPath, LOCK_PATH);
        curatorMutex = new InterProcessMutex(curatorClient, lockPath);

        LOGGER.info("Acquiring ZK lock on {}...", lockPath);
        try {
            if (!curatorMutex.acquire(10, TimeUnit.SECONDS)) {
                LOGGER.error("Failed to acquire ZK lock. Are you running another framework named {}? Exiting.",
                        serviceSpec.getName());
                SchedulerUtils.hardExit(SchedulerErrorCode.LOCK_UNAVAILABLE);
            }
        } catch (Exception ex) {
            LOGGER.error("Error acquiring ZK lock.", ex);
        }
    }

    private void unlock() {
        try {
            curatorMutex.release();
        } catch (Exception ex) {
            LOGGER.error("Error releasing ZK lock.", ex);
        }
    }

    private void initCurator() {
        curatorClient = CuratorFrameworkFactory.newClient(zkConnectionString, CuratorUtils.getDefaultRetry());
        curatorClient.start();
    }

    protected void init() {
        this.zkConnectionString = this.serviceSpec.getZookeeperConnection();
        initCurator();
        lock();

        this.apiPort = this.serviceSpec.getApiPort();
        this.stateStore = DefaultScheduler.createStateStore(this.serviceSpec, zkConnectionString);

        try {
            configTargetStore = DefaultScheduler.createConfigStore(serviceSpec, zkConnectionString, Arrays.asList());
        } catch (ConfigStoreException e) {
            LOGGER.error("Unable to create config store", e);
            throw new IllegalStateException(e);
        }

        ConfigurationUpdater.UpdateResult configUpdateResult = DefaultScheduler
                .updateConfig(serviceSpec, stateStore, configTargetStore);

        this.targetConfigId = configUpdateResult.targetId;
        offerRequirementProvider = DefaultScheduler.createOfferRequirementProvider(stateStore, targetConfigId);
    }

    @VisibleForTesting
    protected Collection<Plan> generatePlansFromRawSpec(RawServiceSpecification rawServiceSpecification)
            throws Exception {
        DefaultPlanGenerator planGenerator = new DefaultPlanGenerator(configTargetStore, stateStore);
        List<Plan> plans = new LinkedList<>();
        if (rawServiceSpecification.getPlans() != null) {
            for (Map.Entry<String, RawPlan> entry : rawServiceSpecification.getPlans().entrySet()) {
                plans.add(planGenerator.generate(entry.getValue(), entry.getKey(), serviceSpec.getPods()));
            }
        }
        return plans;
    }

    /**
     * Creates and registers the service with Mesos, while starting a Jetty HTTP API service on the
     * {@code apiPort}.
     */
    @Override
    public void register(ServiceSpec serviceSpecification, Collection<Plan> plans) {
        DefaultScheduler defaultScheduler = DefaultScheduler.create(
                serviceSpecification,
                plans,
                stateStore,
                configTargetStore,
                offerRequirementProvider);

        startApiServer(defaultScheduler, apiPort);
        registerFramework(defaultScheduler, getFrameworkInfo(), "zk://" + zkConnectionString + "/mesos");
    }

    public ServiceSpec getServiceSpec() {
        return serviceSpec;
    }


    public Collection<Plan> getPlans() {
        return plans;
    }

    private static void startApiServer(DefaultScheduler defaultScheduler, int apiPort) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JettyApiServer apiServer = null;
                try {
                    LOGGER.info("Starting API server.");
                    apiServer = new JettyApiServer(apiPort, defaultScheduler.getResources());
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

    private static void registerFramework(Scheduler sched, Protos.FrameworkInfo frameworkInfo, String masterUri) {
        LOGGER.info("Registering framework: {}", frameworkInfo);
        SchedulerDriver driver = new SchedulerDriverFactory().create(sched, frameworkInfo, masterUri);
        driver.run();
    }

    private Protos.FrameworkInfo getFrameworkInfo() {
        Protos.FrameworkInfo.Builder fwkInfoBuilder = Protos.FrameworkInfo.newBuilder()
                .setName(serviceSpec.getName())
                .setFailoverTimeout(TWO_WEEK_SEC)
                .setUser(USER)
                .setRole(SchedulerUtils.nameToRole(serviceSpec.getName()))
                .setPrincipal(SchedulerUtils.nameToPrincipal(serviceSpec.getName()))
                .setCheckpoint(true);

        // The framework ID is not available when we're being started for the first time.
        Optional<Protos.FrameworkID> optionalFrameworkId = stateStore.fetchFrameworkId();
        if (optionalFrameworkId.isPresent()) {
            fwkInfoBuilder.setId(optionalFrameworkId.get());
        }

        return fwkInfoBuilder.build();
    }
}