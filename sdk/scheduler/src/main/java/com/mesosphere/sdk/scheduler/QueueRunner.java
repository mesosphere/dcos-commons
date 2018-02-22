package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.curator.CuratorLocker;
import com.mesosphere.sdk.curator.CuratorPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterCache;
import com.mesosphere.sdk.storage.PersisterException;

/**
 * Sets up and executes a {@link FrameworkRunner} to which {@link ServiceScheduler}s may be added.
 */
public class QueueRunner implements Runnable {

    private final SchedulerConfig schedulerConfig;
    private final FrameworkConfig frameworkConfig;
    private final Persister persister;
    private final MultiMesosEventClient clients;

    public static QueueRunner build() {
        SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
        FrameworkConfig frameworkConfig = FrameworkConfig.fromEnv();
        Persister persister;
        try {
            persister = schedulerConfig.isStateCacheEnabled()
                    ? new PersisterCache(CuratorPersister.newBuilder(
                            frameworkConfig.frameworkName, frameworkConfig.zookeeperConnection).build())
                    : CuratorPersister.newBuilder(
                            frameworkConfig.frameworkName, frameworkConfig.zookeeperConnection).build();
        } catch (PersisterException e) {
            throw new IllegalStateException(String.format(
                    "Failed to initialize default persister at %s for framework %s",
                    frameworkConfig.zookeeperConnection, frameworkConfig.frameworkName));
        }
        return new QueueRunner(schedulerConfig, frameworkConfig, persister);

    }

    private QueueRunner(SchedulerConfig schedulerConfig, FrameworkConfig frameworkConfig, Persister persister) {
        this.schedulerConfig = schedulerConfig;
        this.frameworkConfig = frameworkConfig;
        this.persister = persister;
        this.clients = new MultiMesosEventClient();
    }

    // TODO(nickbp): This (or something like it) should be called by the 'add a service' HTTP endpoint
    public void addService(MesosEventClient service) {
        clients.addClient(service);
    }

    /**
     * Runs the queue. Don't forget to call this!
     * This should never exit, instead the entire process will be terminated internally.
     */
    @Override
    public void run() {
        CuratorLocker.lock(frameworkConfig.frameworkName, frameworkConfig.zookeeperConnection);
        Metrics.configureStatsd(schedulerConfig);

        new FrameworkRunner(schedulerConfig, frameworkConfig).registerAndRunFramework(persister, clients);
    }
}
