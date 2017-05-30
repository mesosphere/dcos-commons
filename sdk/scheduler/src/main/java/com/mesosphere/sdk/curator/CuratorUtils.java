package com.mesosphere.sdk.curator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;

/**
 * A set of common utilites for managing Curator/Zookeeper paths and data.
 */
class CuratorUtils {

    private static final int DEFAULT_CURATOR_POLL_DELAY_MS = 1000;
    private static final int DEFAULT_CURATOR_MAX_RETRIES = 3;

    /**
     * Escape sequence to use for slashes in service names. Slashes are used in DC/OS for folders, and we don't want to
     * confuse ZK with those.
     */
    private static final String FRAMEWORK_NAME_SLASH_ESCAPE = "__";

    /**
     * Name to use for storing a reverse mapping of the service name. This is lowercased as it's something that's being
     * handled in the underlying Persister layer (like {@link CuratorLocker}'s "lock"), whereas common storage handling
     * in the {@code storage} package is PascalCased.
     */
    private static final String SERVICE_NAME_NODE = "servicename";

    /**
     * This must never change, as it affects the serialization of the ServiceName node.
     */
    private static final Charset SERVICE_NAME_CHARSET = StandardCharsets.UTF_8;

    private CuratorUtils() {
        // do not instantiate
    }

    /**
     * Returns a reasonable default retry policy for querying ZK.
     */
    static RetryPolicy getDefaultRetry() {
        return new ExponentialBackoffRetry(
                CuratorUtils.DEFAULT_CURATOR_POLL_DELAY_MS, CuratorUtils.DEFAULT_CURATOR_MAX_RETRIES);
    }

    /**
     * Returns the root node to store all scheduler ZK data inside. For example:
     *
     * <ul>
     * <li>"your-name-here" => /dcos-service-your-name-here</li>
     * <li>"/path/to/your-name-here" => /dcos-service-path.to.your-name-here</li>
     * </ul>
     */
    static String getServiceRootPath(String frameworkName) {
        if (frameworkName.startsWith(PersisterUtils.PATH_DELIM_STR)) {
            // Trim any leading slash
            frameworkName = frameworkName.substring(PersisterUtils.PATH_DELIM_STR.length());
        }
        // Replace any other slashes (e.g. from folder support) with dots:
        frameworkName = frameworkName.replace(PersisterUtils.PATH_DELIM_STR, FRAMEWORK_NAME_SLASH_ESCAPE);
        // dcos-service-<your.name.here>
        return DcosConstants.SERVICE_ROOT_PATH_PREFIX + frameworkName;
    }

    /**
     * Compares the service name to the previously stored name in zookeeper, or creates a new node containing this data
     * if it isn't already present. This is useful for two situations where foldered service names may be confused with
     * literal dot-delimited names:
     *
     * <ul>
     * <li>Protecting against collisions if someone uses periods in their service names. For example, a foldered service
     * named "/myteam/kafka" could collide with a non-foldered service named "myteam.kafka" as the prior is converted
     * to the latter where ZK is concerned.</li>
     * <li>Allowing deterministic mapping of a given ZK node back to the originating service. For example, the data in
     * "dcos-service-myteam.kafka" could be for a service named "myteam.kafka", or for a foldered service named
     * "/myteam/kafka". Storing the name explicitly allows potential tooling to tell which one it is. This isn't
     * currently needed but could be useful someday.</li>
     * </ul>
     *
     * @param persister the persister where the service name should be written
     * @param serviceName the service name to check for equality, or to write if no service name node exists
     * @see CuratorUtils#getServiceRootPath(String)
     */
    static void initServiceName(Persister persister, String serviceName) {
        try {
            byte[] bytes = persister.get(SERVICE_NAME_NODE);
            if (bytes.length == 0) {
                throw new IllegalArgumentException(String.format(
                        "Invalid data when fetching service name in '%s'", SERVICE_NAME_NODE));
            }
            String currentServiceName = new String(bytes, SERVICE_NAME_CHARSET);
            if (!currentServiceName.equals(serviceName)) {
                throw new IllegalArgumentException(String.format(
                        "Collision between similar service names: Expected name '%s', but stored name is '%s'.",
                        serviceName, currentServiceName));
            }
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // The service name doesn't exist yet, either due to a new install or an upgrade from a prior version
                // that doesn't store this information. Initialize.
                try {
                    persister.set(SERVICE_NAME_NODE, serviceName.getBytes(SERVICE_NAME_CHARSET));
                } catch (PersisterException e2) {
                    throw new IllegalStateException("Failed to store service name", e2);
                }
            } else {
                throw new IllegalStateException("Failed to fetch prior service name for validation", e);
            }
        }
    }
}
