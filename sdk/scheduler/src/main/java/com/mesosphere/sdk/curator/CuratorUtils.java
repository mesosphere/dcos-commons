package com.mesosphere.sdk.curator;

import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;

import com.mesosphere.sdk.dcos.DcosConstants;

/**
 * A set of common utilites for managing Curator/Zookeeper paths and data.
 */
class CuratorUtils {

    private static final int DEFAULT_CURATOR_POLL_DELAY_MS = 1000;
    private static final int DEFAULT_CURATOR_MAX_RETRIES = 3;

    private CuratorUtils() {
        // do not instantiate
    }

    /**
     * Returns a reasonable default retry policy for querying ZK.
     */
    public static RetryPolicy getDefaultRetry() {
        return new ExponentialBackoffRetry(
                CuratorUtils.DEFAULT_CURATOR_POLL_DELAY_MS, CuratorUtils.DEFAULT_CURATOR_MAX_RETRIES);
    }

    /**
     * Returns the root node to store all scheduler ZK data inside.
     */
    public static String getServiceRootPath(String frameworkName) {
        if (frameworkName.startsWith("/")) {
            // "/dcos-service-" + "/foo"
            return DcosConstants.SERVICE_ROOT_PATH_PREFIX + frameworkName.substring(1);
        } else {
            // "/dcos-service-" + "foo"
            return DcosConstants.SERVICE_ROOT_PATH_PREFIX + frameworkName;
        }
    }
}
