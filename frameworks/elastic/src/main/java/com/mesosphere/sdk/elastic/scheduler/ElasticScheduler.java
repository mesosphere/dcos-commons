package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.apache.mesos.scheduler.DefaultScheduler;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.plan.strategy.ParallelStrategy;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class ElasticScheduler extends DefaultScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticScheduler.class);
    private static final String SERIAL = "SERIAL";
    private static final String PARALLEL = "PARALLEL";
    private final String strategy = Optional.ofNullable(System.getenv("PLAN_STRATEGY")).orElse(SERIAL);

    ElasticScheduler(ServiceSpecification serviceSpecification,
                     StateStore stateStore,
                     ConfigStore<ServiceSpecification> configStore,
                     Collection<ConfigurationValidator<ServiceSpecification>> configValidators,
                     Integer permanentFailureTimeoutSec,
                     Integer destructiveRecoveryDelaySec) {
        super(serviceSpecification, stateStore, configStore, configValidators,
                Optional.of(permanentFailureTimeoutSec), destructiveRecoveryDelaySec);
    }

    @Override
    protected void initializeDeploymentPlanManager() {
        LOGGER.info(String.format("Initializing Elastic deployment plan with %s strategy...", strategy));
        DefaultStepFactory stepFactory = new DefaultStepFactory(
                configStore,
                stateStore,
                offerRequirementProvider,
                taskSpecificationProvider);
        DefaultPhaseFactory phaseFactory = new DefaultPhaseFactory(stepFactory);
        List<Phase> phases = serviceSpecification.getTaskSets().stream().
                map(taskSet -> phaseFactory.getPhase(taskSet,
                        strategy.equals(PARALLEL) ? new ParallelStrategy<>() : new SerialStrategy<>())).
                collect(Collectors.toList());
        Plan plan = new DefaultPlan("elastic_plan", phases,
                strategy.equals(PARALLEL) ? new ParallelStrategy<>() : new SerialStrategy<>());
        deploymentPlanManager = new DefaultPlanManager(plan);
    }

}
