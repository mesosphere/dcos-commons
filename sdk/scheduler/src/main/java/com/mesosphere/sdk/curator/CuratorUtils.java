package com.mesosphere.sdk.curator;

import com.mesosphere.sdk.dcos.DcosConstants;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.ACLProvider;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A set of common utilites for managing Curator/Zookeeper paths and data.
 */
public class CuratorUtils {
    /**
     * This must never change, as it affects the serialization of the SchemaVersion node.
     *
     * @see CuratorSchemaVersionStore
     */
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private static final String PATH_DELIM = "/";

    private static final int DEFAULT_CURATOR_POLL_DELAY_MS = 1000;
    private static final int DEFAULT_CURATOR_MAX_RETRIES = 3;
    /**
     * Default Session and Timeout used by Curator Framework.
     */
    public static final int DEFAULT_SESSION_TIMEOUT_MS = 60 * 1000;
    public static final int DEFAULT_CONNECTION_TIMEOUT_MS = 15 * 1000;

    public static RetryPolicy getDefaultRetry() {
        return new ExponentialBackoffRetry(
                CuratorUtils.DEFAULT_CURATOR_POLL_DELAY_MS,
                CuratorUtils.DEFAULT_CURATOR_MAX_RETRIES);
    }

    private CuratorUtils() {
        // do not instantiate
    }

    public static String toServiceRootPath(String frameworkName) {
        if (frameworkName.startsWith("/")) {
            // "/dcos-service-" + "/foo"
            return DcosConstants.SERVICE_ROOT_PATH_PREFIX + frameworkName.substring(1);
        } else {
            // "/dcos-service-" + "foo"
            return DcosConstants.SERVICE_ROOT_PATH_PREFIX + frameworkName;
        }
    }

    public static String join(final String first, final String second) {
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

    static List<String> getParentPaths(final String path) {
        // /path/to/thing => ["/path", "/path/to"] (skip "/" and "/path/to/thing")
        List<String> paths = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        String[] elements = path.split(PATH_DELIM);
        for (int i = 0; i + 1 < elements.length; ++i) { // skip last element, if any
            if (elements[i].isEmpty()) {
                continue;
            }
            if (i == 0) {
                // Only include a "/" prefix if the input had a "/" prefix:
                if (path.startsWith("/")) {
                    builder.append(PATH_DELIM);
                }
            } else {
                builder.append(PATH_DELIM);
            }
            builder.append(elements[i]);
            paths.add(builder.toString());
        }
        return paths;
    }

    static byte[] serialize(final Object str) {
        return str.toString().getBytes(CHARSET);
    }

    static String deserialize(byte[] data) {
        return new String(data, CHARSET);
    }

    /**
     * Creates a curator framework client with username and password
     * for zookeeper authorization.
     * @param connectionString
     * @param retryPolicy
     * @param username
     * @param password
     * @return Curator Framework.
     */
    public static CuratorFramework getClientWithAcl(String connectionString,
                                                    RetryPolicy retryPolicy,
                                                    String username,
                                                    String password) {
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                .connectString(connectionString)
                .retryPolicy(retryPolicy)
                .connectionTimeoutMs(DEFAULT_CONNECTION_TIMEOUT_MS)
                .sessionTimeoutMs(DEFAULT_SESSION_TIMEOUT_MS);

        List<ACL> acls = new ArrayList<ACL>();
        acls.addAll(ZooDefs.Ids.CREATOR_ALL_ACL);
        acls.addAll(ZooDefs.Ids.READ_ACL_UNSAFE);

        String authenticationString = username + ":" + password;
        builder.authorization("digest", authenticationString.getBytes(StandardCharsets.UTF_8))
                .aclProvider(new ACLProvider() {
                    @Override
                    public List<ACL> getDefaultAcl() {
                        return acls;
                    }

                    @Override
                    public List<ACL> getAclForPath(String path) {
                        return acls;
                    }
                });

        return builder.build();
    }
}
