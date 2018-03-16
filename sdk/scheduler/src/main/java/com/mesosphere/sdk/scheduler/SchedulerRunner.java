package com.mesosphere.sdk.scheduler;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.curator.CuratorLocker;
import com.mesosphere.sdk.generated.SDKBuildInfo;
import com.mesosphere.sdk.http.HealthResource;
import com.mesosphere.sdk.http.PlansResource;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPlanManager;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.PersisterException;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.slf4j.Logger;

import java.io.File;
import java.time.Instant;
import java.util.*;

/**
 * Class which sets up and executes the correct {@link AbstractScheduler} instance.
 */
public class SchedulerRunner implements Runnable {
    private static final Logger LOGGER = LoggingUtils.getLogger(SchedulerRunner.class);

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
        SchedulerConfig schedulerConfig = schedulerBuilder.getSchedulerConfig();

        LOGGER.info("Build information:\n- {}: {}, built {}\n- SDK: {}/{}, built {}",
                schedulerConfig.getPackageName(),
                schedulerConfig.getPackageVersion(),
                Instant.ofEpochMilli(schedulerConfig.getPackageBuildTimeMs()),

                SDKBuildInfo.VERSION,
                SDKBuildInfo.GIT_SHA,
                Instant.ofEpochMilli(SDKBuildInfo.BUILD_TIME_EPOCH_MS));
    }

    /**
     * Runs the scheduler. Don't forget to call this!
     * This should never exit, instead the entire process will be terminated internally.
     */
    @Override
    public void run() {
        CuratorLocker locker = new CuratorLocker(schedulerBuilder.getServiceSpec());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutdown initiated, releasing curator lock");
            locker.unlock();
        }));
        locker.lock();

        SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
        Metrics.configureStatsd(schedulerConfig);
        AbstractScheduler scheduler = schedulerBuilder.build();
        scheduler.start();
        Optional<Scheduler> mesosScheduler = scheduler.getMesosScheduler();
        if (mesosScheduler.isPresent()) {
            SchedulerApiServer apiServer = new SchedulerApiServer(schedulerConfig, scheduler.getResources());
            apiServer.start(new AbstractLifeCycle.AbstractLifeCycleListener() {
                @Override
                public void lifeCycleStarted(LifeCycle event) {
                    scheduler.markApiServerStarted();
                }
            });

            runScheduler(
                    scheduler.frameworkInfo,
                    mesosScheduler.get(),
                    schedulerBuilder.getServiceSpec(),
                    schedulerBuilder.getSchedulerConfig(),
                    schedulerBuilder.getStateStore());
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

            PlanManager emptyPlanManager = DefaultPlanManager.createProceeding(emptyDeployPlan);
            PlansResource emptyPlanResource = new PlansResource();
            emptyPlanResource.setPlanManagers(Arrays.asList(emptyPlanManager));

            schedulerBuilder.getStateStore().clearAllData();

            SchedulerApiServer apiServer = new SchedulerApiServer(
                    schedulerConfig,
                    Arrays.asList(
                            emptyPlanResource,
                            new HealthResource()));
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
            SchedulerConfig schedulerConfig,
            StateStore stateStore) {
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
