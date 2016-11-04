package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.api.JettyApiServer;
import org.apache.mesos.dcos.DcosConstants;
import org.apache.mesos.scheduler.DefaultScheduler;
import org.apache.mesos.scheduler.SchedulerDriverFactory;
import org.apache.mesos.scheduler.SchedulerUtils;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Optional;

/**
 * This class is a default implementation of the Service interface.  It serves mainly as an example
 * with hard-coded values for "user", and "master-uri", and failover timeouts.  More sophisticated
 * services may want to implement the Service interface directly.
 *
 * Customizing the runtime user for individual tasks may be accomplished by customizing the 'user'
 * field on CommandInfo returned by {@link TaskSpecification#getCommand()}.
 */
public class DefaultService implements Service {
    private static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;
    private static final String USER = "root";
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultService.class);

    private final int apiPort;
    private final String zkConnectionString;

    private StateStore stateStore;
    private ServiceSpecification serviceSpecification;

    /**
     * Creates a new instance which when registered will start a Jetty HTTP API service on the
     * provided port, using {@link DcosConstants#MESOS_MASTER_ZK_CONNECTION_STRING} as the ZK
     * connection string.
     *
     * @param apiPort the port to run the HTTP API service against, typically the 'PORT0' envvar
     */
    public DefaultService(int apiPort)  {
        this(apiPort, DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
    }

    /**
     * Creates a new instance which when registered will start a Jetty HTTP API service on the
     * provided port, with support for a custom ZK location for state storage.
     *
     * @param apiPort the port to run the HTTP API service against, typically the 'PORT0' envvar
     * @param zkConnectionString zookeeper connection string to use, of the form "host:port"
     */
    public DefaultService(int apiPort, String zkConnectionString)  {
        this.apiPort = apiPort;
        this.zkConnectionString = zkConnectionString;
    }

    /**
     * Creates and registers the service with Mesos, while starting a Jetty HTTP API service on the
     * {@code apiPort}.
     */
    @Override
    public void register(ServiceSpecification serviceSpecification) {
        this.serviceSpecification = serviceSpecification;
        this.stateStore = DefaultScheduler.createStateStore(serviceSpecification.getName(), zkConnectionString);
        DefaultScheduler defaultScheduler = DefaultScheduler.create(
                serviceSpecification,
                stateStore,
                DefaultScheduler.createConfigStore(
                        serviceSpecification.getName(),
                        zkConnectionString,
                        Collections.emptyList()));
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
