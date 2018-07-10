package com.mesosphere.sdk.scheduler.multi;

import com.mesosphere.sdk.framework.FrameworkConfig;
import com.mesosphere.sdk.framework.FrameworkRunner;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.*;
import com.mesosphere.sdk.state.SchemaVersionStore;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;
import org.slf4j.Logger;

/**
 * Sets up and executes a {@link FrameworkRunner} to which potentially multiple {@link AbstractScheduler}s may be added.
 */
public class MultiServiceRunner implements Runnable {

    private static final Logger LOGGER = LoggingUtils.getLogger(MultiServiceRunner.class);

    /**
     * Builder for {@link MultiServiceRunner}.
     */
    public static class Builder {
        private final SchedulerConfig schedulerConfig;
        private final FrameworkConfig frameworkConfig;
        private final MesosEventClient client;
        private final Persister persister;
        private boolean usingGpus = false;

        private Builder(
                SchedulerConfig schedulerConfig,
                FrameworkConfig frameworkConfig,
                Persister persister,
                MesosEventClient client) {
            this.schedulerConfig = schedulerConfig;
            this.frameworkConfig = frameworkConfig;
            this.persister = persister;
            this.client = client;
        }

        /**
         * Tells Mesos that we want to be offered GPU resources.
         *
         * @return {@code this}
         */
        public Builder enableGpus() {
            this.usingGpus = true;
            return this;
        }

        /**
         * Returns a new {@link MultiServiceRunner} instance which may be launched with {@code run()}.
         */
        public MultiServiceRunner build() {
            // Check and/or initialize schema version before doing any other storage access:
            SchemaVersionStore schemaVersionStore = new SchemaVersionStore(persister);

            if (schedulerConfig.isMonoToMultiMigrationDisabled()) {
                schemaVersionStore.check(SUPPORTED_SCHEMA_VERSION_MULTI_SERVICE);
            } else {
                int curVer = schemaVersionStore.getOrSetVersion(SUPPORTED_SCHEMA_VERSION_MULTI_SERVICE);
                if (curVer == SchedulerRunner.getSupportedSchemaVersionSingleService()) {
                    LOGGER.warn("Found old schema in ZK Storage that can be migrated to a new schema");
                    try {
                        PersisterUtils.backUpFrameworkZKData(persister);
                        PersisterUtils.migrateMonoToMultiZKData(persister);
                        schemaVersionStore.store(SUPPORTED_SCHEMA_VERSION_MULTI_SERVICE);
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

            return new MultiServiceRunner(schedulerConfig, frameworkConfig, persister, client, usingGpus);
        }
    }

    /**
     * Schema version used by single-service schedulers, which is what {@link MultiServiceRunner} runs.
     */
    private static final int SUPPORTED_SCHEMA_VERSION_MULTI_SERVICE = 2;

    private final SchedulerConfig schedulerConfig;
    private final FrameworkConfig frameworkConfig;
    private final Persister persister;
    private final MesosEventClient client;
    private final boolean usingGpus;

    /**
     * Returns a new {@link Builder} instance which may be customize before building the {@link MultiServiceRunner}.
     *
     * @param client the Mesos event client which receives offers/statuses from Mesos. Note that this may route
     *               events to multiple wrapped clients
     */
    public static Builder newBuilder(
            SchedulerConfig schedulerConfig,
            FrameworkConfig frameworkConfig,
            Persister persister,
            MesosEventClient client) {
        return new Builder(schedulerConfig, frameworkConfig, persister, client);
    }

    /**
     * Returns a new {@link MultiServiceRunner} instance which may be launched with {@code run()}.
     *
     * @param client the Mesos event client which receives offers/statuses from Mesos. Note that this may route events
     *               to multiple wrapped clients
     */
    private MultiServiceRunner(
            SchedulerConfig schedulerConfig,
            FrameworkConfig frameworkConfig,
            Persister persister,
            MesosEventClient client,
            boolean usingGpus) {
        this.schedulerConfig = schedulerConfig;
        this.frameworkConfig = frameworkConfig;
        this.persister = persister;
        this.client = client;
        this.usingGpus = usingGpus;
    }

    /**
     * Runs the queue. Don't forget to call this!
     * This should never exit, instead the entire process will be terminated internally.
     */
    @Override
    public void run() {
        Metrics.configureStatsd(schedulerConfig);
        // Note: Single-service schedulers can enable region awareness via a SchedulerBuilder call OR via an envvar.
        // In the case of queues, we just use the envvar, since at this point there is no SchedulerBuilder yet.
        new FrameworkRunner(schedulerConfig, frameworkConfig, usingGpus, schedulerConfig.isRegionAwarenessEnabled())
                .registerAndRunFramework(persister, client);
    }
}
