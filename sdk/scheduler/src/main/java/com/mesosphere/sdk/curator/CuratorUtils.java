package com.mesosphere.sdk.curator;

import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.state.PathUtils;

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
    static RetryPolicy getDefaultRetry() {
        return new ExponentialBackoffRetry(DEFAULT_CURATOR_POLL_DELAY_MS, DEFAULT_CURATOR_MAX_RETRIES);
    }

    /**
     * Returns the root node to store all scheduler ZK data inside.
     */
    static String getServiceRootPath(String serviceName) {
        if (serviceName.startsWith(PathUtils.PATH_DELIM)) {
            // "/dcos-service-" + "/foo"
            return DcosConstants.SERVICE_ROOT_PATH_PREFIX + serviceName.substring(1);
        } else {
            // "/dcos-service-" + "foo"
            return DcosConstants.SERVICE_ROOT_PATH_PREFIX + serviceName;
        }
    }
}
