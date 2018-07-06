package com.mesosphere.sdk.scheduler.multi;

import com.mesosphere.sdk.framework.FrameworkConfig;
import com.mesosphere.sdk.framework.FrameworkRunner;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.MesosEventClient;
import com.mesosphere.sdk.scheduler.Metrics;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.state.SchemaVersionStore;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterUtils;
import org.slf4j.Logger;

/**
 * Sets up and executes a {@link FrameworkRunner} to which potentially multiple {@link AbstractScheduler}s may be added.
 */
public class MultiServiceRunner implements Runnable {

    private static final Logger LOGGER = LoggingUtils.getLogger(MultiServiceEventClient.class);

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

            int curVer = schemaVersionStore.getOrSetVersion(SUPPORTED_SCHEMA_VERSION_MULTI_SERVICE);
            if (curVer < SUPPORTED_SCHEMA_VERSION_MULTI_SERVICE) {
                LOGGER.warn("Found old schema in ZK Storage. Triggering backup and migrate");
                backUpFrameworkZKData(persister);
                migrateMonoServiceToMultiService(persister, frameworkConfig, schedulerConfig);
            }

            return new MultiServiceRunner(schedulerConfig, frameworkConfig, persister, client, usingGpus);
        }

        private void backUpFrameworkZKData(Persister persister) {
            persister.recursiveCopy();
        }


        private void migrateMonoServiceToMultiService(
                Persister persister,
                FrameworkConfig frameworkConfig,
                SchedulerConfig schedulerConfig) {
            /*
             * This is what we need to do to migrate to multi mode:
             * - Create a znode named `Services`
             *   - Create its child named {dcos_service_name}
             *   - Move the ConfigTarget , Configurations , Properties, Tasks Nodes from
             *     Top Level Nodes to be children of above child {dcos_service_name}
             */

            //persister.recursiveCopy(PersisterUtils.SERVICE_NAMESPACE_ROOT_NAME);

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
