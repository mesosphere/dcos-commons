package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.api.JettyApiServer;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.scheduler.DefaultScheduler;
import org.apache.mesos.scheduler.SchedulerDriverFactory;
import org.apache.mesos.scheduler.SchedulerUtils;
import org.apache.mesos.scheduler.plan.Plan;
import org.apache.mesos.specification.yaml.YAMLServiceSpecFactory;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * This class is a default implementation of the Service interface.  It serves mainly as an example
 * with hard-coded values for "user", and "master-uri", and failover timeouts.  More sophisticated
 * services may want to implement the Service interface directly.
 * <p>
 * Customizing the runtime user for individual tasks may be accomplished by customizing the 'user'
 * field on CommandInfo returned by {@link TaskSpecification#getCommand()}.
 */
public class DefaultService implements Service {
    private static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;
    private static final String USER = "root";
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultService.class);

    private int apiPort;
    private String zkConnectionString;

    private StateStore stateStore;
    private ServiceSpec serviceSpec;

    public DefaultService() {}

    public DefaultService(String yamlSpecification) throws IOException {
        final PlanGenerator planGenerator = new DefaultPlanGenerator();
        final ServiceSpec serviceSpecification = YAMLServiceSpecFactory.generateFromYAML(yamlSpecification);
        register(serviceSpecification, planGenerator.generate(serviceSpecification));
    }

    public DefaultService(File pathToYamlSpecification) throws IOException {
        final PlanGenerator planGenerator = new DefaultPlanGenerator();
        final ServiceSpec serviceSpecification = YAMLServiceSpecFactory.generateFromYAML(pathToYamlSpecification);
        register(serviceSpecification, planGenerator.generate(serviceSpecification));
    }

    /**
     * Creates and registers the service with Mesos, while starting a Jetty HTTP API service on the
     * {@code apiPort}.
     */
    @Override
    public void register(ServiceSpec serviceSpecification, Collection<Plan> plans) {

    }

    public void register(ServiceSpec serviceSpec) {
        this.serviceSpec = serviceSpec;
        this.stateStore = DefaultScheduler.createStateStore(serviceSpec, zkConnectionString);
        DefaultScheduler defaultScheduler;
        try {
            defaultScheduler = DefaultScheduler.create(
                    serviceSpec,
                    stateStore,
                    DefaultScheduler.createConfigStore(
                            serviceSpec, zkConnectionString, Collections.emptyList()));
        } catch (ConfigStoreException e) {
            LOGGER.error("Unable to create DefaultScheduler", e);
            throw new IllegalStateException(e);
        }
        startApiServer(defaultScheduler, apiPort);
        registerFramework(defaultScheduler, getFrameworkInfo(), "zk://" + zkConnectionString + "/mesos");

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
