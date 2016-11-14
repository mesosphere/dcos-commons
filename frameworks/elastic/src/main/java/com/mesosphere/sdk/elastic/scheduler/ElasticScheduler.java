package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.apache.mesos.scheduler.DefaultScheduler;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;

public class ElasticScheduler extends DefaultScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticScheduler.class);

    protected ElasticScheduler(ServiceSpecification serviceSpecification, StateStore stateStore, ConfigStore<ServiceSpecification> configStore, Collection<ConfigurationValidator<ServiceSpecification>> configValidators, Optional<Integer> permanentFailureTimeoutSec, Integer destructiveRecoveryDelaySec) {
        super(serviceSpecification, stateStore, configStore, configValidators, permanentFailureTimeoutSec, destructiveRecoveryDelaySec);
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
        DefaultPlanFactory defaultPlanFactory = new DefaultPlanFactory(phaseFactory);
        Plan plan = defaultPlanFactory.getPlan(serviceSpecification);
        deploymentPlanManager = new DefaultPlanManager(plan);
    }

}
