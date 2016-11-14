package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.api.JettyApiServer;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.apache.mesos.scheduler.DefaultScheduler;
import org.apache.mesos.scheduler.SchedulerDriverFactory;
import org.apache.mesos.scheduler.SchedulerUtils;
import org.apache.mesos.specification.Service;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

class ElasticService implements Service {
    private static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;
    private static final Integer DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_SEC = 10 * 60;
    private static final Integer PERMANENT_FAILURE_DELAY_SEC = 20 * 60;
    private static final String USER = "root";
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticService.class);

    private final int apiPort;
    private final String zkConnectionString;
    private final Collection<ConfigurationValidator<ServiceSpecification>> configValidators;

    private StateStore stateStore;
    private ServiceSpecification serviceSpecification;

    ElasticService(int apiPort,
                   String zkConnectionString,
                   Collection<ConfigurationValidator<ServiceSpecification>> configValidators) {
        this.apiPort = apiPort;
        this.zkConnectionString = zkConnectionString;
        List<ConfigurationValidator<ServiceSpecification>> validators =
                new ArrayList<>(DefaultScheduler.defaultConfigValidators());

        validators.addAll(configValidators);
        this.configValidators = validators;
    }

    @Override
    public void register(ServiceSpecification serviceSpecification) {
        LOGGER.info("Registering ElasticService ServiceSpecification...");

        this.serviceSpecification = serviceSpecification;
        this.stateStore = DefaultScheduler.createStateStore(serviceSpecification, zkConnectionString);
        ElasticScheduler elasticScheduler;
        try {
            ConfigStore<ServiceSpecification> configStore = DefaultScheduler.createConfigStore(
                    serviceSpecification, zkConnectionString, Collections.emptyList());
            elasticScheduler = new ElasticScheduler(
                    serviceSpecification,
                    stateStore,
                    configStore,
                    configValidators,
                PERMANENT_FAILURE_DELAY_SEC,
                    DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_SEC);
        } catch (ConfigStoreException e) {
            LOGGER.error("Unable to create ElasticScheduler", e);
            throw new IllegalStateException(e);
        }
        startApiServer(elasticScheduler, apiPort);
        registerFramework(elasticScheduler, getFrameworkInfo(), "zk://" + zkConnectionString + "/mesos");
    }

    private void startApiServer(DefaultScheduler defaultScheduler, int apiPort) {
        new Thread(() -> {
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
        }).start();
    }

    private void registerFramework(Scheduler sched, Protos.FrameworkInfo frameworkInfo, String masterUri) {
        LOGGER.info("Registering framework: {}", frameworkInfo);
        SchedulerDriver driver = new SchedulerDriverFactory().create(sched, frameworkInfo, masterUri);
        driver.run();
    }

    private Protos.FrameworkInfo getFrameworkInfo() {
        Protos.FrameworkInfo.Builder fwkInfoBuilder = Protos.FrameworkInfo.newBuilder()
                .setName(serviceSpecification.getName())
                .setFailoverTimeout(TWO_WEEK_SEC)
                .setUser(USER)
                .setRole(SchedulerUtils.nameToRole(serviceSpecification.getName()))
                .setPrincipal(SchedulerUtils.nameToPrincipal(serviceSpecification.getName()))
                .setCheckpoint(true);

        // The framework ID is not available when we're being started for the first time.
        Optional<Protos.FrameworkID> optionalFrameworkId = stateStore.fetchFrameworkId();
        if (optionalFrameworkId.isPresent()) {
            fwkInfoBuilder.setId(optionalFrameworkId.get());
        }

        return fwkInfoBuilder.build();
    }
}
