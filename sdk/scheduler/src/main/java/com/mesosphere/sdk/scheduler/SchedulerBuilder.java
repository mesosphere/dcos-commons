package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.config.ConfigurationUpdater;
import com.mesosphere.sdk.config.DefaultConfigurationUpdater;
import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.config.validate.DefaultConfigValidators;
import com.mesosphere.sdk.curator.CuratorPersister;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.framework.FrameworkConfig;
import com.mesosphere.sdk.http.endpoints.ArtifactResource;
import com.mesosphere.sdk.http.queries.ArtifactQueries;
import com.mesosphere.sdk.http.types.EndpointProducer;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.evaluate.placement.AndRule;
import com.mesosphere.sdk.offer.evaluate.placement.ExactMatcher;
import com.mesosphere.sdk.offer.evaluate.placement.IsLocalRegionRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementUtils;
import com.mesosphere.sdk.offer.evaluate.placement.RegionRuleFactory;
import com.mesosphere.sdk.scheduler.decommission.DecommissionPlanFactory;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.recovery.DefaultRecoveryPlanManager;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanOverrider;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanOverriderFactory;
import com.mesosphere.sdk.scheduler.recovery.constrain.LaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.constrain.TimedLaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.constrain.UnconstrainedLaunchConstrainer;
import com.mesosphere.sdk.scheduler.recovery.monitor.FailureMonitor;
import com.mesosphere.sdk.scheduler.recovery.monitor.NeverFailureMonitor;
import com.mesosphere.sdk.scheduler.recovery.monitor.TimedFailureMonitor;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawPlan;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterCache;
import com.mesosphere.sdk.storage.PersisterException;

import org.slf4j.Logger;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Creates a new {@link DefaultScheduler}.
 */
public class SchedulerBuilder {

    private final Logger logger;

    private ServiceSpec serviceSpec;
    private final SchedulerConfig schedulerConfig;
    private final Persister persister;

    // When these collections are empty, we don't do anything extra:
    private final Map<String, RawPlan> yamlPlans = new HashMap<>();
    private final Map<String, EndpointProducer> endpointProducers = new HashMap<>();
    private Collection<ConfigValidator<ServiceSpec>> customConfigValidators = new ArrayList<>();
    private Collection<Object> customResources = new ArrayList<>();
    private RecoveryPlanOverriderFactory recoveryPlanOverriderFactory;
    private PlanCustomizer planCustomizer;
    private Optional<String> namespace = Optional.empty();
    private Optional<ArtifactQueries.TemplateUrlFactory> templateUrlFactory = Optional.empty();

    SchedulerBuilder(ServiceSpec serviceSpec, SchedulerConfig schedulerConfig) throws PersisterException {
        this(
                serviceSpec,
                schedulerConfig,
                schedulerConfig.isStateCacheEnabled() ?
                        new PersisterCache(CuratorPersister.newBuilder(serviceSpec).build()) :
                        CuratorPersister.newBuilder(serviceSpec).build());
    }

    SchedulerBuilder(ServiceSpec serviceSpec, SchedulerConfig schedulerConfig, Persister persister) {
        // NOTE: we specifically avoid accessing the provided persister before build() is called.
        // This is to ensure that upstream has a chance to e.g. lock it via CuratorLocker.
        this.logger = LoggingUtils.getLogger(getClass(), serviceSpec.getName());
        this.serviceSpec = serviceSpec;
        this.schedulerConfig = schedulerConfig;
        this.persister = persister;
    }

    /**
     * Returns the {@link ServiceSpec} which was provided via the constructor.
     */
    public ServiceSpec getServiceSpec() {
        return serviceSpec;
    }

    /**
     * Returns the {@link SchedulerConfig} object which was provided via the constructor.
     */
    public SchedulerConfig getSchedulerConfig() {
        return schedulerConfig;
    }

    /**
     * Returns the {@link Persister} object which was provided via the constructor, or which was created by default
     * internally.
     */
    public Persister getPersister() {
        return persister;
    }

    /**
     * Specifies custom endpoint resources which should be exposed through the scheduler's API server, in addition
     * to the defaults.
     */
    public SchedulerBuilder setCustomResources(Collection<Object> customResources) {
        this.customResources = customResources;
        return this;
    }

    /**
     * Specifies a custom list of configuration validators to be run when updating to a new target configuration,
     * or otherwise uses the default validators returned by
     * {@link DefaultConfigValidators#getValidators(SchedulerConfig)}.
     */
    public SchedulerBuilder setCustomConfigValidators(Collection<ConfigValidator<ServiceSpec>> customConfigValidators) {
        this.customConfigValidators = customConfigValidators;
        return this;
    }

    /**
     * Specifies a custom {@link EndpointProducer} to be added to the /endpoints API. This may be used by services
     * which wish to expose custom endpoint information via that API.
     *
     * @param name the name of the endpoint to be exposed
     * @param endpointProducer the producer to be invoked when the provided endpoint name is requested
     */
    public SchedulerBuilder setEndpointProducer(String name, EndpointProducer endpointProducer) {
        endpointProducers.put(name, endpointProducer);
        return this;
    }

    /**
     * Sets the {@link Plan}s from the provided {@link RawServiceSpec} to this instance, using a
     * {@link DefaultPlanGenerator} to handle conversion.
     */
    public SchedulerBuilder setPlansFrom(RawServiceSpec rawServiceSpec) throws ConfigStoreException {
        if (rawServiceSpec.getPlans() != null) {
            this.yamlPlans.clear();
            this.yamlPlans.putAll(rawServiceSpec.getPlans());
        }
        return this;
    }

    /**
     * Assigns a {@link RecoveryPlanOverriderFactory} to be used for generating the recovery plan manager.
     *
     * @param recoveryPlanOverriderFactory the factory which generates the custom recovery plan manager
     */
    public SchedulerBuilder setRecoveryManagerFactory(RecoveryPlanOverriderFactory recoveryPlanOverriderFactory) {
        this.recoveryPlanOverriderFactory = recoveryPlanOverriderFactory;
        return this;
    }

    /**
     * Assigns a {@link PlanCustomizer} to be used for customizing plans.
     *
     * @param planCustomizer the plan customizer
     */
    public SchedulerBuilder setPlanCustomizer(PlanCustomizer planCustomizer) {
        this.planCustomizer = planCustomizer;
        return this;
    }

    /**
     * Configures the resulting scheduler instance with a region constraint.
     */
    public SchedulerBuilder withSingleRegionConstraint() {
         if (!Capabilities.getInstance().supportsDomains()) {
             // If this is an older version of DC/OS that doesn't support multi-region deployments, this is a noop.
             return this;
         }

         Optional<String> schedulerRegion = schedulerConfig.getSchedulerRegion();
         PlacementRule regionRule = getRegionRule(schedulerRegion);

         List<PodSpec> updatedPodSpecs = serviceSpec.getPods().stream()
                 .map(p -> podWithPlacementRule(p, regionRule))
                 .collect(Collectors.toList());

         DefaultServiceSpec.Builder builder = DefaultServiceSpec.newBuilder(serviceSpec).pods(updatedPodSpecs);
         if (schedulerRegion.isPresent()) {
             builder.region(schedulerRegion.get());
         }
         serviceSpec = builder.build();

         return this;
    }

    @VisibleForTesting
    static PlacementRule getRegionRule(Optional<String> schedulerRegion) {
        if (!schedulerRegion.isPresent()) {
            return new IsLocalRegionRule();
        }

        return RegionRuleFactory.getInstance().require(ExactMatcher.create(schedulerRegion.get()));
    }

    private static PodSpec podWithPlacementRule(PodSpec podSpec, PlacementRule placementRule) {
        if (podSpec.getPlacementRule().isPresent()) {
            placementRule = new AndRule(placementRule, podSpec.getPlacementRule().get());
        }

        return DefaultPodSpec.newBuilder(podSpec).placementRule(placementRule).build();
    }

    /**
     * Assigns a custom namespace for this service. This is only relevant when a single framework is running multiple
     * services, where different services need to be differentiated from each other. The namespace is used for both
     * storage in ZK ({@link StateStore}/{@link ConfigStore}), as well as in resource labels.
     */
    public SchedulerBuilder setNamespace(String namespace) {
        this.namespace = Optional.of(namespace);
        return this;
    }

    /**
     * Assigns a custom factory for generating config template URLs. Otherwise a default suitable for use with
     * {@link ArtifactResource} is used.
     */
    public SchedulerBuilder setTemplateUrlFactory(ArtifactQueries.TemplateUrlFactory templateUrlFactory) {
        this.templateUrlFactory = Optional.of(templateUrlFactory);
        return this;
    }

    /**
     * Creates a new Mesos scheduler instance with the provided values or their defaults, or an empty {@link Optional}
     * if no Mesos scheduler should be registered for this run.
     *
     * @return a new Mesos scheduler instance to be registered, or an empty {@link Optional}
     * @throws IllegalArgumentException if validating the provided configuration failed
     */
    public AbstractScheduler build() {
        // NOTE: we specifically avoid accessing the provided persister before build() is called.
        // This is to ensure that upstream has a chance to e.g. lock it via CuratorLocker.

        // When multi-service is enabled, state/configs are stored within a namespace matching the service name.
        // Otherwise use an empty namespace, which indicates single-service mode.
        String namespaceStr = namespace.orElse("");
        FrameworkStore frameworkStore = new FrameworkStore(persister);
        StateStore stateStore = new StateStore(persister, namespaceStr);
        ConfigStore<ServiceSpec> configStore = new ConfigStore<>(
                DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister, namespaceStr);
        Capabilities.getInstance().setSchedulerConfig(schedulerConfig);

        if (schedulerConfig.isUninstallEnabled()) {
            // FRAMEWORK UNINSTALL: The scheduler and all its service(s) are being uninstalled. Launch this service in
            // uninstall mode. UninstallScheduler will internally flag the stateStore with an uninstall bit if needed.
            return new UninstallScheduler(
                    serviceSpec,
                    frameworkStore,
                    stateStore,
                    configStore,
                    FrameworkConfig.fromServiceSpec(serviceSpec),
                    schedulerConfig,
                    Optional.ofNullable(planCustomizer));
        }

        if (StateStoreUtils.isUninstalling(stateStore)) {
            // SERVICE UNINSTALL: The service has an uninstall bit set in its (potentially namespaced) state store.
            if (namespace.isPresent()) {
                // This namespaced service is partway through being removed from the parent multi-service scheduler.
                // Launch the service in uninstall mode so that it can continue with whatever may be left.
                return new UninstallScheduler(
                        serviceSpec,
                        frameworkStore,
                        stateStore,
                        configStore,
                        FrameworkConfig.fromServiceSpec(serviceSpec),
                        schedulerConfig,
                        Optional.ofNullable(planCustomizer));
            } else {
                // This is an illegal state for a single-service scheduler. SchedulerConfig's uninstall bit should have
                // also been enabled. If we got here, it means that the user likely tampered with the scheduler env
                // after having previously triggered an uninstall, which had set the bit in stateStore. Just exit,
                // because the service is likely now in an inconsistent state resulting from the incomplete uninstall.
                logger.error("Service has been previously told to uninstall, this cannot be reversed. " +
                        "Reenable the uninstall flag to complete the process.");
                SchedulerUtils.hardExit(SchedulerErrorCode.SCHEDULER_ALREADY_UNINSTALLING);
            }
        }

        try {
            return getDefaultScheduler(frameworkStore, stateStore, configStore);
        } catch (ConfigStoreException e) {
            logger.error("Failed to construct scheduler.", e);
            SchedulerUtils.hardExit(SchedulerErrorCode.INITIALIZATION_FAILURE);
            return null; // This is so the compiler doesn't complain.  The scheduler is going down anyway.
        }
    }

    /**
     * Creates a new scheduler instance with the provided values or their defaults.
     *
     * @return a new scheduler instance
     * @throws IllegalArgumentException if config validation failed when updating the target config.
     */
    private DefaultScheduler getDefaultScheduler(
            FrameworkStore frameworkStore,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore) throws ConfigStoreException {

        // Determine whether deployment had previously completed BEFORE we update the config.
        // Plans may be generated from the config content.
        boolean hasCompletedDeployment = StateStoreUtils.getDeploymentWasCompleted(stateStore);
        if (!hasCompletedDeployment) {
            try {
                // Check for completion against the PRIOR service spec. For example, if the new service spec has n+1
                // nodes, then we want to check that the prior n nodes had successfully deployed.
                ServiceSpec lastServiceSpec = configStore.fetch(configStore.getTargetConfig());
                Optional<Plan> deployPlan = getDeployPlan(
                        getPlans(stateStore, configStore, lastServiceSpec, yamlPlans));
                if (deployPlan.isPresent() && deployPlan.get().isComplete()) {
                    logger.info("Marking deployment as having been previously completed");
                    StateStoreUtils.setDeploymentWasCompleted(stateStore);
                    hasCompletedDeployment = true;
                }
            } catch (ConfigStoreException e) {
                // This is expected during initial deployment, when there is no prior configuration.
                logger.info("Unable to retrieve last configuration. Assuming that no prior deployment has completed");
            }
        }

        List<PodSpec> pods = serviceSpec.getPods().stream()
                .map(podSpec -> updatePodPlacement(podSpec))
                .collect(Collectors.toList());
        serviceSpec = DefaultServiceSpec.newBuilder(serviceSpec).pods(pods).build();

        // Update/validate config as needed to reflect the new service spec:
        Collection<ConfigValidator<ServiceSpec>> configValidators = new ArrayList<>();
        configValidators.addAll(DefaultConfigValidators.getValidators(schedulerConfig));
        configValidators.addAll(customConfigValidators);
        final ConfigurationUpdater.UpdateResult configUpdateResult =
                updateConfig(serviceSpec, stateStore, configStore, configValidators);
        if (!configUpdateResult.getErrors().isEmpty()) {
            logger.warn("Failed to update configuration due to validation errors: {}", configUpdateResult.getErrors());
            try {
                // If there were errors, stick with the last accepted target configuration.
                serviceSpec = configStore.fetch(configStore.getTargetConfig());
            } catch (ConfigStoreException e) {
                // Uh oh. Bail.
                logger.error("Failed to retrieve previous target configuration", e);
                throw new IllegalArgumentException(e);
            }
        }

        // Now that a ServiceSpec has been chosen, generate the plans.
        Collection<Plan> plans = getPlans(stateStore, configStore, serviceSpec, yamlPlans);
        plans = selectDeployPlan(plans, hasCompletedDeployment);
        Optional<Plan> deployPlan = getDeployPlan(plans);
        if (!deployPlan.isPresent()) {
            throw new IllegalArgumentException("No deploy plan provided: " + plans);
        }

        List<String> errors = configUpdateResult.getErrors().stream()
                .map(ConfigValidationError::toString)
                .collect(Collectors.toList());

        if (!errors.isEmpty()) {
            plans = setDeployPlanErrors(plans, deployPlan.get(), errors);
        }

        PlanManager deploymentPlanManager =
                DefaultPlanManager.createProceeding(getDeployPlan(plans).get());
        PlanManager recoveryPlanManager = getRecoveryPlanManager(
                Optional.ofNullable(recoveryPlanOverriderFactory),
                stateStore,
                configStore,
                plans);
        Optional<PlanManager> decommissionPlanManager = getDecommissionPlanManager(stateStore);
        PlanCoordinator planCoordinator = buildPlanCoordinator(
                serviceSpec.getName(),
                deploymentPlanManager,
                recoveryPlanManager,
                decommissionPlanManager,
                plans);

        return new DefaultScheduler(
                serviceSpec,
                FrameworkConfig.fromServiceSpec(serviceSpec),
                schedulerConfig,
                namespace,
                customResources,
                planCoordinator,
                Optional.ofNullable(planCustomizer),
                frameworkStore,
                stateStore,
                configStore,
                templateUrlFactory.orElse(ArtifactResource.getUrlFactory(serviceSpec.getName())),
                endpointProducers);
    }

    private PlanManager getRecoveryPlanManager(
            Optional<RecoveryPlanOverriderFactory> recoveryOverriderFactory,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Collection<Plan> plans) {

        List<RecoveryPlanOverrider> overrideRecoveryPlanManagers = new ArrayList<>();
        if (recoveryOverriderFactory.isPresent()) {
            logger.info("Adding overriding recovery plan manager.");
            overrideRecoveryPlanManagers.add(recoveryOverriderFactory.get().create(stateStore, plans));
        }
        final LaunchConstrainer launchConstrainer;
        final FailureMonitor failureMonitor;
        if (serviceSpec.getReplacementFailurePolicy().isPresent()) {
            ReplacementFailurePolicy failurePolicy = serviceSpec.getReplacementFailurePolicy().get();
            launchConstrainer = new TimedLaunchConstrainer(
                    Duration.ofMinutes(failurePolicy.getMinReplaceDelayMin()));
            failureMonitor = new TimedFailureMonitor(
                    Duration.ofMinutes(failurePolicy.getPermanentFailureTimoutMin()),
                    stateStore,
                    configStore);
        } else {
            launchConstrainer = new UnconstrainedLaunchConstrainer();
            failureMonitor = new NeverFailureMonitor();
        }
        return new DefaultRecoveryPlanManager(
                stateStore,
                configStore,
                PlanUtils.getLaunchableTasks(plans),
                launchConstrainer,
                failureMonitor,
                overrideRecoveryPlanManagers);
    }

    private Optional<PlanManager> getDecommissionPlanManager(StateStore stateStore) {
        DecommissionPlanFactory decommissionPlanFactory = new DecommissionPlanFactory(serviceSpec, stateStore);
        Optional<Plan> decommissionPlan = decommissionPlanFactory.getPlan();
        if (decommissionPlan.isPresent()) {
            return Optional.of(
                    new DecommissionPlanManager(
                            decommissionPlan.get(),
                            decommissionPlanFactory.getTasksToDecommission()));
        }

        return Optional.empty();
    }

    private static PlanCoordinator buildPlanCoordinator(
            String serviceName,
            PlanManager deploymentPlanManager,
            PlanManager recoveryPlanManager,
            Optional<PlanManager> decommissionPlanManager,
            Collection<Plan> plans) {

        final Collection<PlanManager> planManagers = new ArrayList<>();
        planManagers.add(deploymentPlanManager);
        planManagers.add(recoveryPlanManager);

        if (decommissionPlanManager.isPresent()) {
            planManagers.add(decommissionPlanManager.get());
        }

        // Other custom plan managers
        planManagers.addAll(plans.stream()
                .filter(plan -> !plan.isDeployPlan())
                .map(DefaultPlanManager::createInterrupted)
                .collect(Collectors.toList()));

        return new DefaultPlanCoordinator(planManagers);
    }

    /**
     * Update pods with appropriate placement constraints to enforce user REGION intent.
     * If a pod's placement rules do not explicitly reference a REGION the assumption should be that
     * the user intends that a pod be restriced to the local REGION.
     *
     * @param podSpec The {@link PodSpec} whose placement rule will be updated to enforce appropriate region placement.
     * @return The updated {@link PodSpec}
     */
    static PodSpec updatePodPlacement(PodSpec podSpec) {
        if (!Capabilities.getInstance().supportsDomains() || PlacementUtils.placementRuleReferencesRegion(podSpec)) {
            return podSpec;
        }

        PlacementRule rule;
        if (podSpec.getPlacementRule().isPresent()) {
            rule = new AndRule(new IsLocalRegionRule(), podSpec.getPlacementRule().get());
        } else {
            rule = new IsLocalRegionRule();
        }

        return DefaultPodSpec.newBuilder(podSpec).placementRule(rule).build();
    }

    /**
     * Gets or generate plans against the given service spec.
     *
     * @param stateStore The state store to use for plan generation.
     * @param configStore The config store to use for plan generation.
     * @return a collection of plans
     */
    private Collection<Plan> getPlans(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            ServiceSpec serviceSpec,
            Map<String, RawPlan> yamlPlans) {
        final String plansType;
        final Collection<Plan> plans;
        if (!yamlPlans.isEmpty()) {
            plansType = "YAML";
            // Note: Any internal Plan generation must only be AFTER updating/validating the config. Otherwise plans
            // may look at the old config and mistakenly think they're COMPLETE.
            DefaultPlanGenerator planGenerator = new DefaultPlanGenerator(configStore, stateStore);
            plans = yamlPlans.entrySet().stream()
                    .map(e -> planGenerator.generate(e.getValue(), e.getKey(), serviceSpec.getPods()))
                    .collect(Collectors.toList());
        } else {
            plansType = "generated";
            try {
                if (!configStore.list().isEmpty()) {
                    PlanFactory planFactory = new DeployPlanFactory(
                            new DefaultPhaseFactory(new DefaultStepFactory(configStore, stateStore)));
                    plans = Arrays.asList(planFactory.getPlan(configStore.fetch(configStore.getTargetConfig())));
                } else {
                    plans = Collections.emptyList();
                }
            } catch (ConfigStoreException e) {
                throw new IllegalStateException(e);
            }
        }

        logger.info("Got {} {} plan{}: {}",
                plans.size(),
                plansType,
                plans.size() == 1 ? "" : "s",
                plans.stream().map(plan -> plan.getName()).collect(Collectors.toList()));
        return plans;
    }

    private static Optional<Plan> getDeployPlan(Collection<Plan> plans) {
        List<Plan> deployPlans = plans.stream().filter(Plan::isDeployPlan).collect(Collectors.toList());

        if (deployPlans.size() == 1) {
            return Optional.of(deployPlans.get(0));
        } else if (deployPlans.size() == 0) {
            return Optional.empty();
        } else {
            throw new IllegalStateException(String.format("Found multiple deploy plans: %s", deployPlans));
        }
    }

    /**
     * Updates the Deploy plan in the provided list of {@code plans} to contain the provided {@code errors}.
     * Returns a new list of plans containing the updates.
     */
    private static Collection<Plan> setDeployPlanErrors(
            Collection<Plan> allPlans, Plan deployPlan, List<String> errors) {
        Collection<Plan> updatedPlans = new ArrayList<>();
        updatedPlans.add(new DefaultPlan(
                deployPlan.getName(),
                deployPlan.getChildren(),
                deployPlan.getStrategy(),
                errors));
        for (Plan plan : allPlans) {
            if (!plan.isDeployPlan()) {
                updatedPlans.add(plan);
            }
        }

        return updatedPlans;
    }

    /**
     * Replaces the deploy plan with an update plan, in the case that an update deployment is being performed AND that
     * a custom update plan has been specified.
     */
    @VisibleForTesting
    Collection<Plan> selectDeployPlan(Collection<Plan> plans, boolean hasCompletedDeployment) {
        Optional<Plan> updatePlanOptional = plans.stream()
                .filter(plan -> plan.getName().equals(Constants.UPDATE_PLAN_NAME))
                .findFirst();

        if (!hasCompletedDeployment || !updatePlanOptional.isPresent()) {
            logger.info("Using regular deploy plan. (Has completed deployment: {}, Custom update plan defined: {})",
                    hasCompletedDeployment, updatePlanOptional.isPresent());
            return plans;
        }

        logger.info("Overriding deploy plan with custom update plan. " +
                "(Has completed deployment: {}, Custom update plan defined: {})",
                hasCompletedDeployment, updatePlanOptional.isPresent());
        Collection<Plan> newPlans = new ArrayList<>();
        newPlans.addAll(plans.stream()
                .filter(plan -> !plan.isDeployPlan() && !plan.getName().equals(Constants.UPDATE_PLAN_NAME))
                .collect(Collectors.toList()));
        newPlans.add(new DefaultPlan(
                Constants.DEPLOY_PLAN_NAME,
                updatePlanOptional.get().getChildren(),
                updatePlanOptional.get().getStrategy(),
                Collections.emptyList()));
        return newPlans;
    }

    /**
     * Updates the configuration target to reflect the provided {@code serviceSpec} using the provided
     * {@code configValidators}, or stays with the previous configuration if there were validation errors.
     *
     * @param serviceSpec the service specification to use
     * @param stateStore the state store to pass to the updater
     * @param configStore the config store to pass to the updater
     * @param configValidators the list of config validators,
     *                         see {@link DefaultConfigValidators#getValidators(SchedulerConfig)} for reasonable
     *                         defaults
     * @return the config update result, which may contain one or more validation errors produced by
     *     {@code configValidators}
     */
    private ConfigurationUpdater.UpdateResult updateConfig(
            ServiceSpec serviceSpec,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Collection<ConfigValidator<ServiceSpec>> configValidators) {
        logger.info("Updating config with {} validators...", configValidators.size());
        ConfigurationUpdater<ServiceSpec> configurationUpdater = new DefaultConfigurationUpdater(
                stateStore, configStore, DefaultServiceSpec.getComparatorInstance(), configValidators);
        try {
            return configurationUpdater.updateConfiguration(serviceSpec);
        } catch (ConfigStoreException e) {
            logger.error("Fatal error when performing configuration update. Service exiting.", e);
            throw new IllegalStateException(e);
        }
    }
}
