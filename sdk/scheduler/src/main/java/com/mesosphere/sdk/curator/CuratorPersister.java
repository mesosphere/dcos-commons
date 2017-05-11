package com.mesosphere.sdk.curator;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.PathUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.ACLProvider;
import org.apache.curator.framework.api.transaction.CuratorTransaction;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The Curator implementation of the {@link Persister} interface provides for persistence and retrieval of data from
 * Zookeeper. All paths passed to this instance are automatically namespaced within a framework-specific znode to avoid
 * conflicts with other users of the same ZK instance.
 */
public class CuratorPersister implements Persister {

    private static final Logger logger = LoggerFactory.getLogger(CuratorPersister.class);

    /**
     * Number of times to attempt an atomic write transaction in storeMany().
     * This transaction should only fail if other parties are modifying the data.
     */
    private static final int ATOMIC_WRITE_ATTEMPTS = 3;

    private final String serviceRootPath;
    private final CuratorFramework client;

    /**
     * Builder for constructing {@link CuratorPersister} instances.
     */
    public static class Builder {
        private final String serviceName;
        private final String connectionString;
        private RetryPolicy retryPolicy;
        private String username;
        private String password;

        /**
         * Creates a new {@link Builder} instance which has been initialized with reasonable default values.
         *
         * @param serviceName the framework/service name under which data will be stored
         */
        private Builder(ServiceSpec serviceSpec) {
            this.serviceName = serviceSpec.getName();
            // Set defaults for customizable options:
            this.connectionString = serviceSpec.getZookeeperConnection();
            this.retryPolicy = CuratorUtils.getDefaultRetry();
            this.username = "";
            this.password = "";
        }

        /**
         * Assigns a custom retry policy for the ZK server.
         *
         * @param retryPolicy The custom {@link RetryPolicy}
         */
        public Builder setRetryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        /**
         * Assigns credentials to be used when contacting ZK.
         *
         * @param username The ZK username
         * @param password The ZK password
         */
        public Builder setCredentials(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        /**
         * Returns a new {@link CuratorPersister} instance using the provided settings, using reasonable defaults where
         * custom values were not specified.
         */
        public CuratorPersister build() {
            CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                    .connectString(connectionString)
                    .retryPolicy(retryPolicy);
            final CuratorFramework client;

            if (username.isEmpty() && password.isEmpty()) {
                client = builder.build();
            } else if (!username.isEmpty() && !password.isEmpty()) {
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
                client = builder.build();
            } else {
                throw new IllegalArgumentException(
                        "username and password must both be provided, or both must be empty.");
            }

            return new CuratorPersister(serviceName, client);
        }
    }

    /**
     * Creates a new {@link Builder} instance which has been initialized with reasonable default values.
     *
     * @param serviceSpec the service for which data will be stored
     */
    public static Builder newBuilder(ServiceSpec serviceSpec) {
        return new Builder(serviceSpec);
    }

    @VisibleForTesting
    CuratorPersister(String serviceName, CuratorFramework client) {
        this.serviceRootPath = CuratorUtils.getServiceRootPath(serviceName);
        this.client = client;
        this.client.start();
    }

    @Override
    public byte[] get(String unprefixedPath) throws PersisterException {
        final String path = withFrameworkPrefix(unprefixedPath);
        try {
            return client.getData().forPath(path);
        } catch (KeeperException.NoNodeException e) {
            throw new PersisterException(Reason.NOT_FOUND, String.format("Path to get does not exist: %s", path), e);
        } catch (Exception e) {
            throw new PersisterException(Reason.STORAGE_ERROR,
                    String.format("Unable to retrieve data from %s", path), e);
        }
    }

    @Override
    public Collection<String> getChildren(String unprefixedPath) throws PersisterException {
        final String path = withFrameworkPrefix(unprefixedPath);
        try {
            return new TreeSet<>(client.getChildren().forPath(path));
        } catch (KeeperException.NoNodeException e) {
            throw new PersisterException(Reason.NOT_FOUND, String.format("Path to list does not exist: %s", path), e);
        } catch (Exception e) {
            throw new PersisterException(Reason.STORAGE_ERROR, String.format("Unable to get children of %s", path), e);
        }
    }

    @Override
    public void delete(String unprefixedPath) throws PersisterException {
        final String path = withFrameworkPrefix(unprefixedPath);
        try {
            client.delete().deletingChildrenIfNeeded().forPath(path);
        } catch (KeeperException.NoNodeException e) {
            throw new PersisterException(Reason.NOT_FOUND, String.format("Path to delete does not exist: %s", path), e);
        } catch (Exception e) {
            throw new PersisterException(Reason.STORAGE_ERROR, String.format("Unable to delete %s", path), e);
        }
    }

    @Override
    public void set(String unprefixedPath, byte[] bytes) throws PersisterException {
        final String path = withFrameworkPrefix(unprefixedPath);
        try {
            try {
                client.create().creatingParentsIfNeeded().forPath(path, bytes);
            } catch (KeeperException.NodeExistsException e) {
                client.setData().forPath(path, bytes);
            }
        } catch (Exception e) {
            throw new PersisterException(Reason.STORAGE_ERROR,
                    String.format("Unable to set %d bytes in %s", bytes.length, path), e);
        }
    }

    @Override
    public void setMany(Map<String, byte[]> unprefixedPathBytesMap) throws PersisterException {
        if (unprefixedPathBytesMap.isEmpty()) {
            return;
        }
        Map<String, byte[]> pathBytesMap = new TreeMap<>(); // use consistent ordering
        for (Map.Entry<String, byte[]> entry : unprefixedPathBytesMap.entrySet()) {
            pathBytesMap.put(withFrameworkPrefix(entry.getKey()), entry.getValue());
        }
        try {
            for (int i = 0; i < ATOMIC_WRITE_ATTEMPTS; ++i) {
                // Phase 1: Determine which nodes already exist. This determination can be rendered
                //          invalid by an out-of-band change to the data.
                final Set<String> pathsWhichExist = selectPathsWhichExist(pathBytesMap.keySet());
                List<String> parentPathsToCreate = getParentPathsToCreate(pathBytesMap.keySet(), pathsWhichExist);

                logger.debug("Atomic write attempt {}/{}:\n-Parent paths: {}\n-All paths: {}\n-Paths which exist: {}",
                        i + 1, ATOMIC_WRITE_ATTEMPTS, parentPathsToCreate, pathBytesMap.keySet(), pathsWhichExist);

                // Phase 2: Compose a single transaction that updates and/or creates nodes according to
                //          the above determinations (retry if there's an out-of-band modification)
                final CuratorTransactionFinal transaction =
                        getTransaction(pathBytesMap, pathsWhichExist, parentPathsToCreate);
                if (i + 1 < ATOMIC_WRITE_ATTEMPTS) {
                    try {
                        transaction.commit();
                        break; // Success!
                    } catch (Exception e) {
                        // Transaction failed! Bad connection? Existence check rendered invalid?
                        // Swallow exception and try again
                        logger.error(String.format("Failed to complete transaction attempt %d/%d: %s",
                                i + 1, ATOMIC_WRITE_ATTEMPTS, transaction), e);
                    }
                } else {
                    // Last try: Any exception should be forwarded upstream
                    transaction.commit();
                }
            }
        } catch (Exception e) {
            throw new PersisterException(Reason.STORAGE_ERROR, e);
        }
    }

    @Override
    public void close() {
        client.close();
    }

    private Set<String> selectPathsWhichExist(Set<String> paths) throws Exception {
        Set<String> pathsWhichExist = new HashSet<>();
        for (String path : paths) {
            if (client.checkExists().forPath(path) != null) {
                pathsWhichExist.add(path);
            }
        }
        return pathsWhichExist;
    }

    private List<String> getParentPathsToCreate(Set<String> paths, Set<String> pathsWhichExist) throws Exception {
        List<String> parentPathsToCreate = new ArrayList<>();
        for (String path : paths) {
            if (pathsWhichExist.contains(path)) {
                continue;
            }
            // Transaction interface doesn't support creatingParentsIfNeeded(), so go manual.
            for (String parentPath : PathUtils.getParentPaths(path)) {
                if (client.checkExists().forPath(parentPath) == null
                        && !parentPathsToCreate.contains(parentPath)) {
                    parentPathsToCreate.add(parentPath);
                }
            }
        }
        return parentPathsToCreate;
    }

    private CuratorTransactionFinal getTransaction(
            Map<String, byte[]> pathBytesMap, Set<String> pathsWhichExist, List<String> parentPathsToCreate)
                    throws Exception {
        CuratorTransactionFinal transactionFinal = null;
        CuratorTransaction transaction = client.inTransaction();
        for (String parentPath : parentPathsToCreate) {
            transactionFinal = transaction.create().forPath(parentPath).and();
            transaction = transactionFinal;
        }
        for (Map.Entry<String, byte[]> entry : pathBytesMap.entrySet()) {
            if (pathsWhichExist.contains(entry.getKey())) {
                transactionFinal = transaction.setData().forPath(entry.getKey(), entry.getValue()).and();
            } else {
                transactionFinal = transaction.create().forPath(entry.getKey(), entry.getValue()).and();
            }
            transaction = transactionFinal;
        }
        return transactionFinal;
    }

    /**
     * Maps the provided external path into a framework-namespaced path.
     */
    private String withFrameworkPrefix(String path) {
        path = PathUtils.join(serviceRootPath, path);
        // Avoid any trailing slashes, which lead to STORAGE_ERRORs:
        while (path.endsWith(PathUtils.PATH_DELIM)) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
