package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.curator.CuratorLocker;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;

import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Sets up and executes the {@link ServiceScheduler} instance.
 */
public class SchedulerRunner implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerRunner.class);

    private final SchedulerBuilder schedulerBuilder;

    /**
     * Builds a new instance using a {@link RawServiceSpec} representing the raw object model of a YAML service
     * specification file.
     *
     * @param rawServiceSpec the object model of a YAML service specification file
     * @param schedulerConfig the scheduler configuration to use (usually based on process environment)
     * @param configTemplateDir the directory where any configuration templates are located (usually the parent
     *                          directory of the YAML service specification file)
     * @return a new {@link SchedulerRunner} instance, which may be launched with {@link #run()}
     */
    public static SchedulerRunner fromRawServiceSpec(
            RawServiceSpec rawServiceSpec, SchedulerConfig schedulerConfig, File configTemplateDir) throws Exception {
        return fromSchedulerBuilder(DefaultScheduler.newBuilder(
                DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerConfig, configTemplateDir).build(),
                schedulerConfig)
                .setPlansFrom(rawServiceSpec));
    }

    /**
     * Builds a new instance using a {@link ServiceSpec} representing the serializable Java representation of a service
     * specification.
     *
     * @param serviceSpec the service specification converted to be used by the config store
     * @param schedulerConfig the scheduler configuration to use (usually based on process environment)
     * @return a new {@link SchedulerRunner} instance, which may be launched with {@link #run()}
     */
    public static SchedulerRunner fromServiceSpec(
            ServiceSpec serviceSpec, SchedulerConfig schedulerConfig)
                    throws PersisterException {
        return fromSchedulerBuilder(DefaultScheduler.newBuilder(serviceSpec, schedulerConfig));
    }

    /**
     * Builds a new instance using a {@link SchedulerBuilder} instance representing the scheduler logic to be executed.
     *
     * @param schedulerBuilder the (likely customized) scheduler object to be run by the runner
     * @return a new {@link SchedulerRunner} instance, which may be launched with {@link #run()}
     */
    public static SchedulerRunner fromSchedulerBuilder(SchedulerBuilder schedulerBuilder) {
        return new SchedulerRunner(schedulerBuilder);
    }

    private SchedulerRunner(SchedulerBuilder schedulerBuilder) {
        this.schedulerBuilder = schedulerBuilder;
    }

    /**
     * Runs the scheduler. Don't forget to call this!
     * This should never exit, instead the entire process will be terminated internally.
     */
    @Override
    public void run() {
        CuratorLocker.lock(
                schedulerBuilder.getServiceSpec().getName(),
                schedulerBuilder.getServiceSpec().getZookeeperConnection());

        Metrics.configureStatsd(schedulerBuilder.getSchedulerConfig());

        FrameworkRunner frameworkRunner = new FrameworkRunner(
                schedulerBuilder.getSchedulerConfig(),
                FrameworkConfig.fromServiceSpec(schedulerBuilder.getServiceSpec()));

        ServiceScheduler scheduler = schedulerBuilder.build();
        scheduler.start();
        if (scheduler instanceof UninstallScheduler && ((UninstallScheduler) scheduler).shouldRegisterFramework()) {
            LOGGER.info("Not registering framework because there are no resources left to unreserve.");

            try {
                PersisterUtils.clearAllData(scheduler.getPersister());
            } catch (PersisterException e) {
                throw new IllegalStateException("Unable to clear all data", e);
            }

            SchedulerApiServer apiServer =
                    new SchedulerApiServer(schedulerBuilder.getSchedulerConfig(), scheduler.getResources());
            apiServer.start(new AbstractLifeCycle.AbstractLifeCycleListener() {
                @Override
                public void lifeCycleStarted(LifeCycle event) {
                    LOGGER.info("Started trivially healthy API server.");
                }
            });
        } else {
            frameworkRunner.registerAndRunFramework(scheduler.getPersister(), scheduler);
        }
    }
}
