package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.api.JettyApiServer;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.config.ConfigurationUpdater;
import com.mesosphere.sdk.config.validate.ConfigurationValidator;
import com.mesosphere.sdk.offer.OfferRequirementProvider;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerDriverFactory;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.specification.DefaultPlanGenerator;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpecification;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for the Elastic framework.
 */
public class ElasticService {
    private static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;
    private static final String USER = "root";
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticService.class);

    private int apiPort;
    private String zkConnectionString;
    private DefaultScheduler defaultScheduler;
    private Protos.FrameworkInfo frameworkInfo;
    private StateStore stateStore;
    private ServiceSpec serviceSpec;
    private ConfigStore<ServiceSpec> configTargetStore;
    private OfferRequirementProvider offerRequirementProvider;
    private File pathToYamlSpecification;
    private List<ConfigurationValidator<ServiceSpec>> validators;

    ElasticService(File pathToYamlSpecification) {
        this.pathToYamlSpecification = pathToYamlSpecification;
    }

    Protos.FrameworkInfo getFrameworkInfo() {
        return frameworkInfo;
    }

    List<ConfigurationValidator<ServiceSpec>> getValidators() {
        return validators;
    }

    void init() throws Exception {
        RawServiceSpecification rawServiceSpecification =
                YAMLServiceSpecFactory.generateRawSpecFromYAML(pathToYamlSpecification);
        this.serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpecification);
        this.apiPort = serviceSpec.getApiPort();

        this.zkConnectionString = getZookeeperConnection();
        this.stateStore = DefaultScheduler.createStateStore(serviceSpec, zkConnectionString);
        try {
            this.configTargetStore = DefaultScheduler.createConfigStore(serviceSpec, zkConnectionString,
                    Collections.emptyList());
        } catch (ConfigStoreException e) {
            LOGGER.error("Unable to create config store", e);
            throw new IllegalStateException(e);
        }

        ConfigurationUpdater.UpdateResult configUpdateResult = DefaultScheduler.updateConfig(serviceSpec, stateStore,
                configTargetStore);

        this.offerRequirementProvider = DefaultScheduler.createOfferRequirementProvider(stateStore,
                configUpdateResult.targetId);
        Collection<Plan> plans = generatePlansFromRawSpec(rawServiceSpecification);
        this.validators = new ArrayList<>(DefaultScheduler.defaultConfigValidators());
        this.validators.addAll(configValidators());
        this.defaultScheduler = DefaultScheduler.create(
                serviceSpec,
                plans,
                stateStore,
                configTargetStore,
                offerRequirementProvider,
                validators);
        this.frameworkInfo = buildFrameworkInfo();
    }

    void register() {
        startApiServer();
        registerFramework();
    }

    @CheckReturnValue
    String getZookeeperConnection() {
        return serviceSpec.getZookeeperConnection();
    }

    private void startApiServer() {
        new Thread(() -> {
            JettyApiServer apiServer = null;
            try {
                LOGGER.info("Starting API server for Elastic framework.");
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
        }).start();
    }

    private void registerFramework() {
        LOGGER.info("Registering Elastic framework: {}", frameworkInfo);
        SchedulerDriver driver = new SchedulerDriverFactory().create(defaultScheduler, frameworkInfo,
                zkConnectionString);
        driver.run();
    }

    private Collection<Plan> generatePlansFromRawSpec(RawServiceSpecification rawServiceSpecification)
            throws Exception {
        DefaultPlanGenerator planGenerator = new DefaultPlanGenerator(configTargetStore, stateStore,
                offerRequirementProvider);
        List<Plan> plans = new LinkedList<>();
        if (rawServiceSpecification.getPlans() != null) {
            plans.addAll(YAMLServiceSpecFactory.generateRawPlans(rawServiceSpecification).stream()
                    .map(rawPlan -> planGenerator.generate(rawPlan, serviceSpec.getPods()))
                    .collect(Collectors.toList()));
        }
        return plans;
    }

    private Protos.FrameworkInfo buildFrameworkInfo() {
        Protos.FrameworkInfo.Builder fwkInfoBuilder = Protos.FrameworkInfo.newBuilder()
                .setName(serviceSpec.getName())
                .setFailoverTimeout(TWO_WEEK_SEC)
                .setUser(USER)
                .setWebuiUrl("http://kibana-0-server." + serviceSpec.getName() + ".mesos:5601")
                .setRole(SchedulerUtils.nameToRole(serviceSpec.getName()))
                .setPrincipal(SchedulerUtils.nameToPrincipal(serviceSpec.getName()))
                .setCheckpoint(true);

        Optional<Protos.FrameworkID> optionalFrameworkId = stateStore.fetchFrameworkId();
        optionalFrameworkId.ifPresent(fwkInfoBuilder::setId);

        return fwkInfoBuilder.build();
    }

    private List<ConfigurationValidator<ServiceSpec>> configValidators() {
        return Arrays.asList(new HeapCannotExceedHalfMem(), new MasterTransportPortCannotChange());
    }
}
