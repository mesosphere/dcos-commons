package com.mesosphere.sdk.scheduler;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.config.validate.PodSpecsCannotUseUnsupportedFeatures;
import com.mesosphere.sdk.curator.CuratorLocker;
import com.mesosphere.sdk.framework.FrameworkConfig;
import com.mesosphere.sdk.framework.FrameworkRunner;
import com.mesosphere.sdk.framework.ApiServer;
import com.mesosphere.sdk.framework.SchedulerDriverFactory;
import com.mesosphere.sdk.http.endpoints.HealthResource;
import com.mesosphere.sdk.http.endpoints.PlansResource;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPlanManager;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.SchemaVersionStore;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.slf4j.Logger;

import java.io.File;
import java.util.*;

/**
 * Class which sets up and executes the correct {@link AbstractScheduler} instance.
 */
public class SchedulerRunner implements Runnable {

    private static final Logger LOGGER = LoggingUtils.getLogger(SchedulerRunner.class);

    /**
     * Schema version used by single-service schedulers, which is what {@link SchedulerRunner} runs.
     */
    private static final int SUPPORTED_SCHEMA_VERSION_SINGLE_SERVICE = 1;

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
        SchedulerConfig schedulerConfig = schedulerBuilder.getSchedulerConfig();
        ServiceSpec serviceSpec = schedulerBuilder.getServiceSpec();
        Persister persister = schedulerBuilder.getPersister();

        // Get a curator lock, then check the schema version:
        CuratorLocker.lock(serviceSpec.getName(), serviceSpec.getZookeeperConnection());
        // Check and/or initialize schema version before doing any other storage access:
        new SchemaVersionStore(persister).check(SUPPORTED_SCHEMA_VERSION_SINGLE_SERVICE);

        Metrics.configureStatsd(schedulerConfig);
        AbstractScheduler scheduler = schedulerBuilder.build();
        scheduler.start();
        Optional<Scheduler> mesosScheduler = scheduler.getMesosScheduler();
        if (mesosScheduler.isPresent()) {
            ApiServer apiServer = new ApiServer(schedulerConfig, scheduler.getResources());
            apiServer.start(new AbstractLifeCycle.AbstractLifeCycleListener() {
                @Override
                public void lifeCycleStarted(LifeCycle event) {
                    scheduler.markApiServerStarted();
                }
            });

            runScheduler(
                    new FrameworkRunner(
                            FrameworkConfig.fromServiceSpec(serviceSpec),
                            PodSpecsCannotUseUnsupportedFeatures.serviceRequestsGpuResources(serviceSpec),
                            schedulerBuilder.isRegionAwarenessEnabled())
                            .getFrameworkInfo(new FrameworkStore(schedulerBuilder.getPersister()).fetchFrameworkId()),
                    mesosScheduler.get(),
                    schedulerBuilder.getServiceSpec(),
                    schedulerBuilder.getSchedulerConfig());
        } else {
            /**
             * If no MesosScheduler is provided this scheduler has been deregistered and should report itself healthy
             * and provide an empty COMPLETE deploy plan so it may complete its UNINSTALL.
             *
             * See {@link UninstallScheduler#getMesosScheduler()}.
             */
            Plan emptyDeployPlan = new Plan() {
                @Override
                public List<Phase> getChildren() {
                    return Collections.emptyList();
                }

                @Override
                public Strategy<Phase> getStrategy() {
                    return new SerialStrategy<>();
                }

                @Override
                public UUID getId() {
                    return UUID.randomUUID();
                }

                @Override
                public String getName() {
                    return Constants.DEPLOY_PLAN_NAME;
                }

                @Override
                public List<String> getErrors() {
                    return Collections.emptyList();
                }
            };

            try {
                PersisterUtils.clearAllData(persister);
            } catch (PersisterException e) {
                // Best effort.
                LOGGER.error("Failed to clear all data", e);
            }

            ApiServer apiServer = new ApiServer(
                    schedulerConfig,
                    Arrays.asList(
                            new PlansResource(Collections.singletonList(
                                    DefaultPlanManager.createProceeding(emptyDeployPlan))),
                            new HealthResource(Collections.emptyList())));
            apiServer.start(new AbstractLifeCycle.AbstractLifeCycleListener() {
                @Override
                public void lifeCycleStarted(LifeCycle event) {
                    LOGGER.info("Started trivially healthy API server.");
                }
            });
        }
    }

    private static void runScheduler(
            Protos.FrameworkInfo frameworkInfo,
            Scheduler mesosScheduler,
            ServiceSpec serviceSpec,
            SchedulerConfig schedulerConfig) {
        LOGGER.info("Registering framework: {}", TextFormat.shortDebugString(frameworkInfo));
        String zkUri = String.format("zk://%s/mesos", serviceSpec.getZookeeperConnection());
        Protos.Status status = new SchedulerDriverFactory()
                .create(mesosScheduler, frameworkInfo, zkUri, schedulerConfig)
                .run();
        LOGGER.error("Scheduler driver exited with status: {}", status);
        // DRIVER_STOPPED will occur when we call stop(boolean) during uninstall.
        // When this happens, we want to continue running so that we can advertise that the uninstall plan is complete.
        if (status != Protos.Status.DRIVER_STOPPED) {
            SchedulerUtils.hardExit(SchedulerErrorCode.DRIVER_EXITED);
        }
    }
}
