package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.apache.mesos.scheduler.DefaultScheduler;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.plan.strategy.ParallelStrategy;
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
        LOGGER.info("Initializing Elastic deployment plan...");
        DefaultStepFactory stepFactory = new DefaultStepFactory(
            configStore,
            stateStore,
            offerRequirementProvider,
            taskSpecificationProvider);
        DefaultPhaseFactory phaseFactory = new DefaultPhaseFactory(stepFactory);
        List<Phase> phases = serviceSpecification.getTaskSets().stream().map(taskSet -> phaseFactory.getPhase(taskSet,
            new ParallelStrategy<>())).collect(Collectors.toList());
        Plan plan = new DefaultPlan("elastic_plan", phases, new ParallelStrategy<>());
        deploymentPlanManager = new DefaultPlanManager(plan);
    }

}
