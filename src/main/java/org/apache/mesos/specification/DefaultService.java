package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.api.JettyApiServer;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.scheduler.DefaultScheduler;
import org.apache.mesos.scheduler.SchedulerDriverFactory;
import org.apache.mesos.scheduler.SchedulerUtils;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * This class is a default implementation of the Service interface.  It serves mainly as an example with hard-coded
 * values for "user" and "master-uri", and failover timeouts.  More sophisticated services may want to implement the
 * Service interface directly.
 */
public class DefaultService implements Service {
    private static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;
    private static final String MASTER_URI = "zk://master.mesos:2181/mesos";
    private static final String USER = "root";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final int apiPort;
    private StateStore stateStore;
    private ServiceSpecification serviceSpecification;

    public DefaultService(int apiPort)  {
        this.apiPort = apiPort;
    }

    @Override
    public void register(ServiceSpecification serviceSpecification) {
        this.serviceSpecification = serviceSpecification;
        this.stateStore = new CuratorStateStore(serviceSpecification.getName());
        DefaultScheduler defaultScheduler = new DefaultScheduler(serviceSpecification);
        startApiServer(defaultScheduler);
        registerFramework(defaultScheduler, getFrameworkInfo(), MASTER_URI);
    }

    private void startApiServer(DefaultScheduler defaultScheduler) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JettyApiServer apiServer = null;
                try {
                    logger.info("Starting API server.");
                    apiServer = new JettyApiServer(apiPort, defaultScheduler.getResources());
                    apiServer.start();
                } catch (Exception e) {
                    logger.error("API Server failed with exception: ", e);
                } finally {
                    logger.info("API Server exiting.");
                    try {
                        if (apiServer != null) {
                            apiServer.stop();
                        }
                    } catch (Exception e) {
                        logger.error("Failed to stop API server with exception: ", e);
                    }
                }
            }
        }).start();
    }

    private void registerFramework(Scheduler sched, Protos.FrameworkInfo frameworkInfo, String masterUri) {
        logger.info("Registering framework: " + frameworkInfo);
        SchedulerDriver driver = new SchedulerDriverFactory().create(sched, frameworkInfo, masterUri);
        driver.start();
    }

    private Protos.FrameworkInfo getFrameworkInfo() {
        Protos.FrameworkInfo.Builder fwkInfoBuilder = Protos.FrameworkInfo.newBuilder()
                .setName(serviceSpecification.getName())
                .setFailoverTimeout(TWO_WEEK_SEC)
                .setUser(USER)
                .setRole(SchedulerUtils.nameToRole(serviceSpecification.getName()))
                .setPrincipal(SchedulerUtils.nameToPrincipal(serviceSpecification.getName()))
                .setCheckpoint(true);

        Optional<Protos.FrameworkID> optionalFrameworkId = stateStore.fetchFrameworkId();
        if (optionalFrameworkId.isPresent()) {
            fwkInfoBuilder.setId(optionalFrameworkId.get());
        }

        return fwkInfoBuilder.build();
    }
}
