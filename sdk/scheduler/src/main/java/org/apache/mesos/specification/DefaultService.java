package org.apache.mesos.specification;

import com.google.common.annotations.VisibleForTesting;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.api.JettyApiServer;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.config.ConfigurationUpdater;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.scheduler.DefaultScheduler;
import org.apache.mesos.scheduler.SchedulerDriverFactory;
import org.apache.mesos.scheduler.SchedulerUtils;
import org.apache.mesos.scheduler.plan.Plan;
import org.apache.mesos.specification.yaml.RawPlan;
import org.apache.mesos.specification.yaml.RawServiceSpecification;
import org.apache.mesos.specification.yaml.YAMLServiceSpecFactory;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
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
    private static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;
    private static final String USER = "root";
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultService.class);

    private int apiPort;
    private String zkConnectionString;

    private StateStore stateStore;
    private ServiceSpec serviceSpec;
    private Collection<Plan> plans;
    private ConfigStore<ServiceSpec> configTargetStore;
    private OfferRequirementProvider offerRequirementProvider;

    public DefaultService() {
    }

    public DefaultService(String yamlSpecification) throws Exception {
        final RawServiceSpecification rawServiceSpecification = YAMLServiceSpecFactory
                .generateRawSpecFromYAML(yamlSpecification);
        this.serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpecification);
        init();
        this.plans = generatePlansFromRawSpec(rawServiceSpecification);
        register(serviceSpec, this.plans);
    }

    public DefaultService(File pathToYamlSpecification) throws Exception {
        final RawServiceSpecification rawServiceSpecification = YAMLServiceSpecFactory
                .generateRawSpecFromYAML(pathToYamlSpecification);
        this.serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpecification);
        init();
        this.plans = generatePlansFromRawSpec(rawServiceSpecification);
        register(serviceSpec, this.plans);
    }

    public DefaultService(ServiceSpec serviceSpecification) {
        this.serviceSpec = serviceSpecification;
        init();
        this.plans = Collections.emptyList();
        register(serviceSpecification, this.plans);
    }

    public DefaultService(ServiceSpec serviceSpec, Collection<Plan> plans) {
        this.serviceSpec = serviceSpec;
        init();
        this.plans = plans;
        register(serviceSpec, this.plans);
    }

    private void init() {
        this.apiPort = this.serviceSpec.getApiPort();
        this.zkConnectionString = this.serviceSpec.getZookeeperConnection();
        this.stateStore = DefaultScheduler.createStateStore(this.serviceSpec, zkConnectionString);

        try {
            configTargetStore = DefaultScheduler
                    .createConfigStore(this.serviceSpec, zkConnectionString, Arrays.asList());
        } catch (ConfigStoreException e) {
            LOGGER.error("Unable to create config store", e);
            throw new IllegalStateException(e);
        }

        ConfigurationUpdater.UpdateResult configUpdateResult = DefaultScheduler
                .updateConfig(this.serviceSpec, stateStore, configTargetStore);

        offerRequirementProvider = DefaultScheduler
                .createOfferRequirementProvider(stateStore, configUpdateResult.targetId);
    }

    @VisibleForTesting
    protected Collection<Plan> generatePlansFromRawSpec(RawServiceSpecification rawServiceSpecification)
            throws Exception {
        DefaultPlanGenerator planGenerator = new DefaultPlanGenerator(configTargetStore, stateStore,
                offerRequirementProvider);
        List<Plan> plans = new LinkedList<>();
        if (rawServiceSpecification.getPlans() != null) {
            List<RawPlan> rawPlans = YAMLServiceSpecFactory.generateRawPlans(rawServiceSpecification);
            List<Plan> realPlans = rawPlans.stream()
                    .map(rawPlan -> planGenerator.generate(rawPlan, serviceSpec.getPods()))
                    .collect(Collectors.toList());
            plans.addAll(realPlans);
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
