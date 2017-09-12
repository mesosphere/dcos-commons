package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.config.ConfigurationUpdater;
import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.StrategyGenerator;
import com.mesosphere.sdk.scheduler.plan.strategy.TerminalStrategy;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanOverriderFactory;
import com.mesosphere.sdk.scheduler.uninstall.DeregisterStep;
import com.mesosphere.sdk.specification.DefaultPlanGenerator;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.validation.CapabilityValidator;
import com.mesosphere.sdk.specification.yaml.RawPlan;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.Constants.DEPLOY_PLAN_NAME;

public class AnalyticsScheduler extends DefaultScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsScheduler.class);

    protected DeregisterStep deregisterStep;

    public static class Builder extends DefaultScheduler.Builder {
        private Builder(ServiceSpec serviceSpec, SchedulerFlags schedulerFlags, Persister persister)
            throws PersisterException {
            super(serviceSpec, schedulerFlags, persister);
        }

        public Builder setStateStore(StateStore stateStore) {
            super.setStateStore(stateStore);
            return this;
        }

        public Builder setConfigStore(ConfigStore<ServiceSpec> configStore) {
            super.setConfigStore(configStore);
            return this;
        }

        public Builder setPlansFrom(RawServiceSpec rawServiceSpec) throws ConfigStoreException {
            super.setPlansFrom(rawServiceSpec);
            return this;
        }

        public Collection<Plan> getPlans(StateStore stateStore, ConfigStore<ServiceSpec> configStore, Phase te) {
            final String plansType;
            final Collection<Plan> plans;

            if (!manualPlans.isEmpty()) {
                plansType = "manual";
                plans = new ArrayList<>(manualPlans);  // assume here that you've taken care of tear down yourself
            } else if (!yamlPlans.isEmpty()) {
                plansType = "YAML";
                // Note: Any internal Plan generation must only be AFTER updating/validating the config. Otherwise plans
                // may look at the old config and mistakenly think they're COMPLETE.
                DefaultPlanGenerator planGenerator = new DefaultPlanGenerator(configStore, stateStore);
                plans = yamlPlans.entrySet().stream()
                        .map(e -> planGenerator.generateTerminal(e.getValue(), e.getKey(), serviceSpec.getPods(), te))
                        .collect(Collectors.toList());
            } else {
                plansType = "generated";
                try {
                    if (!configStore.list().isEmpty()) {
                        StrategyGenerator<Phase> strategyGenerator = new TerminalStrategy.Generator<>(false, te);
                        plans = Arrays.asList(
                                new DeployPlanFactory(
                                        new DefaultPhaseFactory(
                                                new DefaultStepFactory(configStore, stateStore)), strategyGenerator)
                                        .getPlan(configStore.fetch(configStore.getTargetConfig()), te));
                    } else {
                        plans = Collections.emptyList();
                    }
                } catch (ConfigStoreException e) {
                    throw new IllegalStateException(e);
                }
            }
            LOGGER.info("Got {} {} plan{}: {}",
                    plans.size(),
                    plansType,
                    plans.size() == 1 ? "" : "s",
                    plans.stream().map(plan -> plan.getName()).collect(Collectors.toList()));
            return plans;
        }

        public AnalyticsScheduler build() {
            try {
                new CapabilityValidator().validate(this.getServiceSpec());
            } catch (CapabilityValidator.CapabilityValidationException e) {
                throw new IllegalStateException("Failed to validate provided ServiceSpec", e);
            }

            // Get custom or default config and state stores (defaults handled by getStateStore()/getConfigStore()):
            final StateStore stateStore = getStateStore();
            final ConfigStore<ServiceSpec> configStore = getConfigStore();
            this.fixLastDeploymentType(stateStore, configStore);

            // Update/validate config as needed to reflect the new service spec:
            Collection<ConfigValidator<ServiceSpec>> configValidators = new ArrayList<>();
            configValidators.addAll(defaultConfigValidators(getSchedulerFlags()));
            configValidators.addAll(customConfigValidators);

            final ConfigurationUpdater.UpdateResult configUpdateResult =
                    updateConfig(this.getServiceSpec(), stateStore, configStore, configValidators);

            if (!configUpdateResult.getErrors().isEmpty()) {
                LOGGER.warn("Failed to update configuration due to errors with configuration {}: {}",
                        configUpdateResult.getTargetId(), configUpdateResult.getErrors());
                try {
                    // If there were errors maintain the last accepted target configuration.
                    serviceSpec = configStore.fetch(configStore.getTargetConfig());
                } catch (ConfigStoreException e) {
                    LOGGER.error("Failed to maintain pervious target configuration.");
                    throw new IllegalStateException(e);
                }
            }

            DeregisterStep deregisterStep = new DeregisterStep(stateStore);
            Phase teardownPhase = new DefaultPhase(
                    "auto-delete",
                    Collections.singletonList(Optional.of(deregisterStep).get()),
                    new SerialStrategy<>(),
                    Collections.emptyList());

            Collection<Plan> plans = getPlans(stateStore, configStore, teardownPhase);
            plans = overrideDeployPlan(plans, configUpdateResult);
            Optional<Plan> deployOptional = getDeployPlan(plans);

            if (!deployOptional.isPresent()) {
                throw new IllegalStateException("No deploy plan provided.");
            }

            List<String> errors = configUpdateResult.getErrors().stream()
                    .map(ConfigValidationError::toString)
                    .collect(Collectors.toList());
            plans = updateDeployPlan(plans, errors);

            LOGGER.info("SENTINEL: finished build");
            return new AnalyticsScheduler(
                    this.getServiceSpec(),
                    this.getSchedulerFlags(),
                    customResources,
                    plans,
                    stateStore,
                    configStore,
                    endpointProducers,
                    Optional.ofNullable(recoveryPlanOverriderFactory),
                    deregisterStep);
        }
   }

   public static Builder newBuilder(ServiceSpec serviceSpec, SchedulerFlags schedulerFlags, Persister persister)
           throws PersisterException {
       return new Builder(serviceSpec, schedulerFlags, persister);
   }

   private void setSchedulerDriverForTerminalPhase(SchedulerDriver driver) {
        this.deregisterStep.setSchedulerDriver(driver);
   }

   @Override
   protected void initializeGlobals(SchedulerDriver driver) throws ConfigStoreException {
        LOGGER.info("Initializing ANALYTICS scheduler");
        setSchedulerDriverForTerminalPhase(driver);
        super.initializeGlobals(driver);
        LOGGER.info("Done initializing ANALYTICS scheduler");
   }

   AnalyticsScheduler(
            ServiceSpec serviceSpec,
            SchedulerFlags schedulerFlags,
            Collection<Object> customResources,
            Collection<Plan> plans,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Map<String, EndpointProducer> customEndpointProducers,
            Optional<RecoveryPlanOverriderFactory> recoveryPlanOverriderFactory,
            DeregisterStep deregisterStep) {
        super(serviceSpec, schedulerFlags, customResources, plans, stateStore, configStore, customEndpointProducers,
                recoveryPlanOverriderFactory);
        this.deregisterStep = deregisterStep;
   }
}
