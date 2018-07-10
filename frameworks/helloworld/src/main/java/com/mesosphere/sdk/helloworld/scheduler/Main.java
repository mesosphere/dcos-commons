package com.mesosphere.sdk.helloworld.scheduler;

import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.curator.CuratorPersister;
import com.mesosphere.sdk.framework.EnvStore;
import com.mesosphere.sdk.framework.FrameworkConfig;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.scheduler.multi.MultiServiceEventClient;
import com.mesosphere.sdk.scheduler.multi.MultiServiceManager;
import com.mesosphere.sdk.scheduler.multi.MultiServiceRunner;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.SchemaVersionStore;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterCache;
import com.mesosphere.sdk.storage.PersisterException;
import com.google.common.base.Splitter;

import com.mesosphere.sdk.storage.PersisterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.state.SchemaVersionStore.SUPPORTED_SCHEMA_VERSION_MULTI_SERVICE;
import static com.mesosphere.sdk.state.SchemaVersionStore.SUPPORTED_SCHEMA_VERSION_SINGLE_SERVICE;

/**
 * Main entry point for the Scheduler.
 */
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final String HELLO_COUNT_ENV_KEY = "HELLO_COUNT";
    private static final String HELLO_CPUS_ENV_KEY = "HELLO_CPUS";
    private static final String FRAMEWORK_GPUS_ENV_KEY = "FRAMEWORK_GPUS";

    public static void main(String[] args) throws Exception {
        final EnvStore envStore = EnvStore.fromEnv();
        final SchedulerConfig schedulerConfig = SchedulerConfig.fromEnvStore(envStore);

        final Collection<Scenario.Type> scenarios = Scenario.getScenarios(envStore);
        LOGGER.info("Using scenarios: {}", scenarios);
        if (scenarios.contains(Scenario.Type.JAVA)) {
            // Create a sample spec in Java, ignore any yaml file args
            LOGGER.info("Starting Java-defined scheduler");
            runJavaDefinedService(schedulerConfig, envStore, scenarios);
            return;
        }

        Collection<File> yamlFiles = getYamlFiles(args);
        if (yamlFiles.size() == 1) {
            // One YAML file: Mono-Scheduler
            LOGGER.info("Starting mono-scheduler using: {}", yamlFiles.iterator().next());
            runSingleYamlService(schedulerConfig, yamlFiles.iterator().next(), scenarios);
        } else {
            if (yamlFiles.isEmpty()) {
                // No YAML files (and not in JAVA scenario): Dynamic Multi-Scheduler (user adds/removes services)
                LOGGER.info("Starting dynamic multi-scheduler");
                runDynamicMultiService(schedulerConfig, envStore, scenarios);
            } else {
                // Multiple YAML files: Static Multi-Scheduler (one service per provided yaml file)
                LOGGER.info("Starting static multi-scheduler using: {}", yamlFiles);
                runFixedMultiYamlService(schedulerConfig, envStore, yamlFiles, scenarios);
            }
        }
    }

    private static void runJavaDefinedService(
            SchedulerConfig schedulerConfig, EnvStore envStore, Collection<Scenario.Type> scenarios)
                    throws PersisterException {
        ServiceSpec javaServiceSpec = createSampleServiceSpec(schedulerConfig, envStore);
        SchedulerBuilder builder = DefaultScheduler.newBuilder(javaServiceSpec, schedulerConfig);
        Scenario.customize(builder, scenarios);
        SchedulerRunner.fromSchedulerBuilder(builder).run();
    }

    /**
     * Starts a scheduler which runs a single fixed service.
     */
    private static void runSingleYamlService(
            SchedulerConfig schedulerConfig, File yamlFile, Collection<Scenario.Type> scenarios) throws Exception {
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(yamlFile).build();
        ServiceSpec serviceSpec =
                DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerConfig, yamlFile.getParentFile())
                .build();
        Persister persister = getPersister(schedulerConfig, FrameworkConfig.fromServiceSpec(serviceSpec));
        SchedulerBuilder builder = DefaultScheduler.newBuilder(serviceSpec, schedulerConfig, persister)
                .setPlansFrom(rawServiceSpec);
        SchedulerRunner.fromSchedulerBuilder(Scenario.customize(builder, scenarios)).run();
    }

    /**
     * Starts a scheduler which allows dynamically adding and removing services. This scheduler is initially in an empty
     * state; services must be manually added before any work is actually performed. If the scheduler is restarted, it
     * will automatically recover its previously-added services.
     */
    private static void runDynamicMultiService(
            SchedulerConfig schedulerConfig,
            EnvStore envStore,
            Collection<Scenario.Type> scenarios) throws Exception {
        FrameworkConfig frameworkConfig = FrameworkConfig.fromEnvStore(envStore);
        Persister persister = getPersister(schedulerConfig, frameworkConfig);
        checkAndMigrate(frameworkConfig, schedulerConfig, persister);
        MultiServiceManager multiServiceManager = new MultiServiceManager();

        ExampleMultiServiceResource httpResource = new ExampleMultiServiceResource(
                schedulerConfig, frameworkConfig, persister, scenarios, multiServiceManager);

        // Recover any previously added services. This MUST be performed to recover the set of active services following
        // a scheduler restart. It also MUST be performed BEFORE we start running the framework thread below.
        httpResource.recover();

        // Set up the client and run the framework:
        MultiServiceEventClient client = new MultiServiceEventClient(
                frameworkConfig.getFrameworkName(),
                schedulerConfig,
                multiServiceManager,
                persister,
                Collections.singleton(httpResource),
                httpResource.getUninstallCallback());

        MultiServiceRunner.Builder runnerBuilder =
                MultiServiceRunner.newBuilder(schedulerConfig, frameworkConfig, persister, client);
        if (envStore.getOptionalBoolean(FRAMEWORK_GPUS_ENV_KEY, false)) {
            runnerBuilder.enableGpus();
        }
        runnerBuilder.build().run();
    }

    /**
     * Starts a scheduler which runs a fixed list of services.
     */
    private static void runFixedMultiYamlService(
            SchedulerConfig schedulerConfig,
            EnvStore envStore,
            Collection<File> yamlFiles,
            Collection<Scenario.Type> scenarios
    ) throws Exception {
        FrameworkConfig frameworkConfig = FrameworkConfig.fromEnvStore(envStore);
        Persister persister = getPersister(schedulerConfig, frameworkConfig);
        checkAndMigrate(frameworkConfig, schedulerConfig, persister);
        MultiServiceManager multiServiceManager = new MultiServiceManager();

        // Add services represented by YAML files to the service manager:
        for (File yamlFile : yamlFiles) {
            RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(yamlFile).build();
            ServiceSpec serviceSpec =
                    DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerConfig, yamlFile.getParentFile())
                    // Override any framework-level params in the servicespec (role, principal, ...) with ours:
                    .setMultiServiceFrameworkConfig(frameworkConfig)
                    .build();
            SchedulerBuilder builder = DefaultScheduler.newBuilder(serviceSpec, schedulerConfig, persister)
                    .setPlansFrom(rawServiceSpec)
                    .enableMultiService(frameworkConfig.getFrameworkName());
            multiServiceManager.putService(Scenario.customize(builder, scenarios).build());
        }

        // Set up the client and run the framework:
        MultiServiceEventClient client = new MultiServiceEventClient(
                frameworkConfig.getFrameworkName(),
                schedulerConfig,
                multiServiceManager,
                persister,
                Collections.emptyList(),
                new MultiServiceEventClient.UninstallCallback() {
            @Override
            public void uninstalled(String name) {
                // Should only happen when the entire framework is being uninstalled, as we do not expose a way to
                // remove added services here.
                LOGGER.info("Service has completed uninstall: {}", name);
            }
        });

        MultiServiceRunner.Builder runnerBuilder =
                MultiServiceRunner.newBuilder(schedulerConfig, frameworkConfig, persister, client);
        if (envStore.getOptionalBoolean(FRAMEWORK_GPUS_ENV_KEY, false)) {
            runnerBuilder.enableGpus();
        }
        runnerBuilder.build().run();
    }

    private static Persister getPersister(SchedulerConfig schedulerConfig, FrameworkConfig frameworkConfig)
            throws PersisterException {
        Persister persister = CuratorPersister.newBuilder(
                frameworkConfig.getFrameworkName(), frameworkConfig.getZookeeperHostPort()).build();
        if (schedulerConfig.isStateCacheEnabled()) {
            persister = new PersisterCache(persister);
        }
        return persister;
    }

    private static Collection<File> getYamlFiles(String[] args) {
        Collection<String> yamlPaths = new ArrayList<>();
        // Support both space-separated or comma-separated files:
        //   ./hello-world foo.yml bar.yml baz.yml
        //   ./hello-world foo.yml,bar.yml,baz.yml
        for (int i = 0; i < args.length; ++i) {
            yamlPaths.addAll(Splitter.on(',').trimResults().splitToList(args[i]));
        }
        LOGGER.info("Using YAML examples: {}", yamlPaths);
        return yamlPaths.stream()
                .map(name -> ExampleMultiServiceResource.getYamlFile(name))
                .collect(Collectors.toList());
    }

    /**
     * Example of constructing a custom ServiceSpec in Java, without a YAML file.
     */
    private static ServiceSpec createSampleServiceSpec(SchedulerConfig schedulerConfig, EnvStore envStore) {
        String podType = "hello";
        String taskName = "hello";

        return DefaultServiceSpec.newBuilder()
                .name("hello-world")
                .principal("hello-world-principal")
                .zookeeperConnection("master.mesos:2181")
                .addPod(DefaultPodSpec.newBuilder(
                        podType,
                        envStore.getRequiredInt(HELLO_COUNT_ENV_KEY),
                        Arrays.asList(DefaultTaskSpec.newBuilder()
                                .name(taskName)
                                .goalState(GoalState.RUNNING)
                                .commandSpec(DefaultCommandSpec.newBuilder(new TaskEnvRouter().getConfig(podType))
                                        .value("echo hello >> hello-container-path/output && sleep 1000")
                                        .build())
                                .resourceSet(DefaultResourceSet
                                        .newBuilder("hello-world-role", Constants.ANY_ROLE, "hello-world-principal")
                                        .id("hello-resources")
                                        .cpus(Double.valueOf(envStore.getRequired(HELLO_CPUS_ENV_KEY)))
                                        .memory(256.0)
                                        .addVolume("ROOT", 5000.0, "hello-container-path")
                                        .build())
                                .build()))
                        .build())
                .build();
    }

    private static void checkAndMigrate(
            FrameworkConfig frameworkConfig,
            SchedulerConfig schedulerConfig,
            Persister persister
    ) {
        // Migrate if needed.
        if (!schedulerConfig.isMonoToMultiMigrationDisabled()) {
            LOGGER.info("Migration is enabled. Analyzing now...");
            SchemaVersionStore schemaVersionStore = new SchemaVersionStore(persister);
            int curVer = schemaVersionStore.getOrSetVersion(SUPPORTED_SCHEMA_VERSION_MULTI_SERVICE);
            if (curVer == SUPPORTED_SCHEMA_VERSION_SINGLE_SERVICE) {
                try {
                    LOGGER.warn("Found old schema in ZK Storage that can be migrated to a new schema");
                    PersisterUtils.backUpFrameworkZKData(persister);
                    PersisterUtils.migrateMonoToMultiZKData(persister, frameworkConfig);
                    schemaVersionStore.store(SUPPORTED_SCHEMA_VERSION_MULTI_SERVICE);
                    LOGGER.warn("Successfully migrated from old schema to new schema!!");
                } catch (PersisterException e) {
                    LOGGER.error("Unable to migrate ZK data : ", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            } else if (curVer == SUPPORTED_SCHEMA_VERSION_MULTI_SERVICE) {
                LOGGER.info("Schema version matches that of multi service mode. Nothing to migrate.");
            } else {
                throw new IllegalStateException(String.format("Storage schema version %d is not supported by " +
                        "this software (expected: %d)", curVer, SUPPORTED_SCHEMA_VERSION_MULTI_SERVICE));
            }
        }
    }
}
