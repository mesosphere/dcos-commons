package org.apache.mesos.curator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * A set of common utilites for managing Curator/Zookeeper paths and data.
 */
class CuratorUtils {

    /**
     * This must never change, as it affects the path to the SchemaVersion object for a given
     * framework name.
     *
     * @see CuratorSchemaVersionStore
     */
    private static final String ROOT_PATH_PREFIX = "/dcos-service-";

    /**
     * This must never change, as it affects the serialization of the SchemaVersion node.
     *
     * @see CuratorSchemaVersionStore
     */
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private static final String PATH_DELIM = "/";

    static final String DEFAULT_CONNECTION_STRING = "master.mesos:2181";
    static final int DEFAULT_CURATOR_POLL_DELAY_MS = 1000;
    static final int DEFAULT_CURATOR_MAX_RETRIES = 3;

    private CuratorUtils() {
        // do not instantiate
    }

    static String toServiceRootPath(String frameworkName) {
        if (frameworkName.startsWith("/")) {
            // "/dcos-service-" + "/foo"
            return ROOT_PATH_PREFIX + frameworkName.substring(1);
        } else {
            // "/dcos-service-" + "foo"
            return ROOT_PATH_PREFIX + frameworkName;
        }
    }

    static String join(final String first, final String second) {
        if (first.endsWith(PATH_DELIM) && second.startsWith(PATH_DELIM)) {
            // "hello/" + "/world"
            return new StringBuilder(first)
                    .deleteCharAt(first.length() - 1)
                    .append(second)
                    .toString();
        } else if (first.endsWith(PATH_DELIM) || second.startsWith(PATH_DELIM)) {
            // "hello/" + "world", or "hello" + "/world"
            return new StringBuilder(first)
                    .append(second)
                    .toString();
        } else {
            // "hello" + "world"
            return new StringBuilder(first)
                    .append(PATH_DELIM)
                    .append(second)
                    .toString();
        }
    }

    static byte[] serialize(final Object str) {
        return str.toString().getBytes(CHARSET);
    }

    static String deserialize(byte[] data) {
        return new String(data, CHARSET);
    }
}
