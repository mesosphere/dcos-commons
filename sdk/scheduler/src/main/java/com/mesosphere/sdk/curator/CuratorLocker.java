package com.mesosphere.sdk.curator;

import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.scheduler.SchedulerErrorCode;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.storage.PersisterUtils;

/**
 * Gets an exclusive lock on service-specific ZK node to ensure two schedulers aren't running simultaneously for the
 * same service.
 */
public class CuratorLocker {
    private static final Logger LOGGER = LoggerFactory.getLogger(CuratorUtils.class);

    private static final int LOCK_ATTEMPTS = 3;
    static final String LOCK_PATH_NAME = "lock";

    private final String serviceName;
    private final String zookeeperConnection;

    private CuratorFramework curatorClient;
    private InterProcessSemaphoreMutex curatorMutex;

    public CuratorLocker(ServiceSpec serviceSpec) {
        this.serviceName = serviceSpec.getName();
        this.zookeeperConnection = serviceSpec.getZookeeperConnection();
    }

    /**
     * Gets an exclusive lock on service-specific ZK node to ensure two schedulers aren't running simultaneously for the
     * same service.
     */
    public void lock() {
        if (curatorClient != null) {
            throw new IllegalStateException("Already locked");
        }
        curatorClient = CuratorFrameworkFactory.newClient(zookeeperConnection, CuratorUtils.getDefaultRetry());
        curatorClient.start();

        final String lockPath = PersisterUtils.join(CuratorUtils.getServiceRootPath(serviceName), LOCK_PATH_NAME);
        InterProcessSemaphoreMutex curatorMutex = new InterProcessSemaphoreMutex(curatorClient, lockPath);

        LOGGER.info("Acquiring ZK lock on {}...", lockPath);
        final String failureLogMsg = String.format("Failed to acquire ZK lock on %s. " +
                "Duplicate service named '%s', or recently restarted instance of '%s'?",
                lockPath, serviceName, serviceName);
        try {
            // Start at 1 for pretty display of "1/3" through "3/3":
            for (int attempt = 1; attempt < LOCK_ATTEMPTS + 1; ++attempt) {
                if (curatorMutex.acquire(10, getWaitTimeUnit())) {
                    LOGGER.info("{}/{} Lock acquired.", attempt, LOCK_ATTEMPTS);
                    this.curatorMutex = curatorMutex;
                    return;
                }
                if (attempt < LOCK_ATTEMPTS) {
                    LOGGER.error("{}/{} {} Retrying lock...", attempt, LOCK_ATTEMPTS, failureLogMsg);
                }
            }
            LOGGER.error(failureLogMsg + " Restarting scheduler process to try again.");
        } catch (Exception ex) {
            LOGGER.error(String.format("Error acquiring ZK lock on path: %s", lockPath), ex);
        }
        curatorClient = null;
        exit();
    }

    /**
     * Releases the lock previously obtained via {@link #lock()}.
     */
    public void unlock() {
        if (curatorClient == null) {
            throw new IllegalStateException("Already unlocked");
        }
        try {
            curatorMutex.release();
        } catch (Exception ex) {
            LOGGER.error("Error releasing ZK lock.", ex);
        }
        curatorClient.close();
        curatorMutex = null;
        curatorClient = null;
    }

    /**
     * Broken out into a separate function to allow overrides in tests.
     */
    @VisibleForTesting
    protected TimeUnit getWaitTimeUnit() {
        return TimeUnit.SECONDS;
    }

    /**
     * Broken out into a separate function to allow overrides in tests.
     */
    @VisibleForTesting
    protected void exit() {
        SchedulerUtils.hardExit(SchedulerErrorCode.LOCK_UNAVAILABLE);
    }
}
