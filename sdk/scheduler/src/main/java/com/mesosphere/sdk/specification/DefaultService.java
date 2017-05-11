package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.curator.CuratorLocker;
import com.mesosphere.sdk.dcos.DcosCertInstaller;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerDriverFactory;
import com.mesosphere.sdk.scheduler.SchedulerErrorCode;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.scheduler.plan.Plan;

import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

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
    protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultService.class);

    private DefaultScheduler.Builder schedulerBuilder;

    private ServiceSpec serviceSpec;

    public DefaultService() {
        //No initialization needed
    }

    public DefaultService(String yamlSpecification, SchedulerFlags schedulerFlags) throws Exception {
        this(YAMLServiceSpecFactory.generateRawSpecFromYAML(yamlSpecification), schedulerFlags);
    }

    public DefaultService(File pathToYamlSpecification, SchedulerFlags schedulerFlags) throws Exception {
        this(YAMLServiceSpecFactory.generateRawSpecFromYAML(pathToYamlSpecification), schedulerFlags);
    }

    public DefaultService(RawServiceSpec rawServiceSpec, SchedulerFlags schedulerFlags) throws Exception {
        this(DefaultScheduler.newBuilder(
                YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, schedulerFlags), schedulerFlags)
                .setPlansFrom(rawServiceSpec));
    }

    public DefaultService(
            ServiceSpec serviceSpecification,
            SchedulerFlags schedulerFlags,
            Collection<Plan> plans) throws Exception {
        this(DefaultScheduler.newBuilder(serviceSpecification, schedulerFlags)
                .setPlans(plans));
    }

    public DefaultService(DefaultScheduler.Builder schedulerBuilder) throws Exception {
        initService(schedulerBuilder);
    }

    protected void initService(DefaultScheduler.Builder schedulerBuilder) throws Exception {
        this.schedulerBuilder = schedulerBuilder;
        this.serviceSpec = schedulerBuilder.getServiceSpec();
    }

    @Override
    public void run() {
        // Install the certs from "$MESOS_SANDBOX/.ssl" (if present) inside the JRE being used to run the scheduler.
        DcosCertInstaller.installCertificate(schedulerBuilder.getSchedulerFlags().getJavaHome());

        CuratorLocker locker = new CuratorLocker(schedulerBuilder.getServiceSpec());
        locker.lock();
        try {
            register();
        } finally {
            locker.unlock();
        }
    }

    /**
     * Creates and registers the service with Mesos, while starting a Jetty HTTP API service on the {@code apiPort}.
     */
    @Override
    public void register() {
        DefaultScheduler defaultScheduler = null;
        try {
            defaultScheduler = schedulerBuilder.build();
        } catch (Throwable e) {
            LOGGER.error("Failed to build scheduler.", e);
            SchedulerUtils.hardExit(SchedulerErrorCode.SCHEDULER_BUILD_FAILED);
        }

        ServiceSpec serviceSpec = schedulerBuilder.getServiceSpec();
        registerAndRunFramework(
                defaultScheduler,
                getFrameworkInfo(serviceSpec, schedulerBuilder.getStateStore()),
                serviceSpec.getZookeeperConnection(),
                schedulerBuilder.getSchedulerFlags());
    }

    protected ServiceSpec getServiceSpec() {
        return this.serviceSpec;
    }

    public static Boolean serviceSpecRequestsGpuResources(ServiceSpec serviceSpec) {
        Collection<PodSpec> pods = serviceSpec.getPods();
        for (PodSpec pod : pods) {
            for (TaskSpec taskSpec : pod.getTasks()) {
                for (ResourceSpec resourceSpec : taskSpec.getResourceSet().getResources()) {
                    if (resourceSpec.getName().equals("gpus") && resourceSpec.getValue().getScalar().getValue() >= 1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void registerAndRunFramework(
            Scheduler sched,
            Protos.FrameworkInfo frameworkInfo,
            String zookeeperHost,
            SchedulerFlags schedulerFlags) {
        LOGGER.info("Registering framework: {}", TextFormat.shortDebugString(frameworkInfo));
        Protos.Status status = new SchedulerDriverFactory().create(
                sched, frameworkInfo, String.format("zk://%s/mesos", zookeeperHost), schedulerFlags).run();
        // TODO(nickbp): Exit scheduler process here?
        LOGGER.error("Scheduler driver exited with status: {}", status);
    }

    private Protos.FrameworkInfo getFrameworkInfo(ServiceSpec serviceSpec, StateStore stateStore) {
        return getFrameworkInfo(serviceSpec, stateStore, USER, TWO_WEEK_SEC);
    }

    protected Protos.FrameworkInfo getFrameworkInfo(
            ServiceSpec serviceSpec,
            StateStore stateStore,
            String userString,
            int failoverTimeoutSec) {
        final String serviceName = serviceSpec.getName();

        Protos.FrameworkInfo.Builder fwkInfoBuilder = Protos.FrameworkInfo.newBuilder()
                .setName(serviceName)
                .setFailoverTimeout(failoverTimeoutSec)
                .setUser(userString)
                .setCheckpoint(true);

        // Use provided role if specified, otherwise default to "<svcname>-role".
        //TODO(nickbp): Use fwkInfoBuilder.addRoles(role) AND fwkInfoBuilder.addCapabilities(MULTI_ROLE)
        if (StringUtils.isEmpty(serviceSpec.getRole())) {
            fwkInfoBuilder.setRole(SchedulerUtils.nameToRole(serviceName));
        } else {
            fwkInfoBuilder.setRole(serviceSpec.getRole());
        }

        // Use provided principal if specified, otherwise default to "<svcname>-principal".
        if (StringUtils.isEmpty(serviceSpec.getPrincipal())) {
            fwkInfoBuilder.setPrincipal(SchedulerUtils.nameToPrincipal(serviceName));
        } else {
            fwkInfoBuilder.setPrincipal(serviceSpec.getPrincipal());
        }

        if (!StringUtils.isEmpty(serviceSpec.getWebUrl())) {
            fwkInfoBuilder.setWebuiUrl(serviceSpec.getWebUrl());
        }

        // The framework ID is not available when we're being started for the first time.
        Optional<Protos.FrameworkID> optionalFrameworkId = stateStore.fetchFrameworkId();
        if (optionalFrameworkId.isPresent()) {
            fwkInfoBuilder.setId(optionalFrameworkId.get());
        }

        if (!StringUtils.isEmpty(serviceSpec.getWebUrl())) {
            fwkInfoBuilder.setWebuiUrl(serviceSpec.getWebUrl());
        }

        if (serviceSpecRequestsGpuResources(serviceSpec)) {
            fwkInfoBuilder.addCapabilities(Protos.FrameworkInfo.Capability.newBuilder()
                    .setType(Protos.FrameworkInfo.Capability.Type.GPU_RESOURCES));
        }

        return fwkInfoBuilder.build();
    }
}
