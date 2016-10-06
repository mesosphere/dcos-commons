package org.apache.mesos.curator;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.transaction.CuratorTransaction;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.mesos.storage.Persister;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The CuratorPersistor implementation of the {@link Persister} interface
 * provides for persistence and retrieval of data from Zookeeper.
 */
public class CuratorPersister implements Persister {

    private static final Logger logger = LoggerFactory.getLogger(CuratorPersister.class);

    /**
     * Number of times to attempt an atomic write transaction in storeMany().
     * This transaction should only fail if other parties are modifying the data.
     */
    private static final int ATOMIC_WRITE_ATTEMPTS = 3;

    private final CuratorFramework client;

    public CuratorPersister(String connectionString, RetryPolicy retryPolicy) {
        this(createClient(connectionString, retryPolicy));
    }

    public CuratorPersister(CuratorFramework client) {
        this.client = client;
    }

    @Override
    public void setMany(Map<String, byte[]> pathBytesMap) throws Exception {
        if (pathBytesMap.isEmpty()) {
            return;
        }
        for (int i = 0; i < ATOMIC_WRITE_ATTEMPTS; ++i) {
            // Phase 1: Determine which nodes already exist. This determination can be rendered
            //          invalid by an out-of-band change to the data.
            final Set<String> pathsWhichExist = selectPathsWhichExist(pathBytesMap.keySet());
            List<String> parentPathsToCreate = getParentPathsToCreate(pathBytesMap.keySet(), pathsWhichExist);

            logger.debug("Atomic write attempt {}/{}:\n-Parent paths: {}\n-All paths: {}\n-Paths which exist: {}",
                    i + 1, ATOMIC_WRITE_ATTEMPTS, parentPathsToCreate, pathBytesMap.keySet(), pathsWhichExist);

            // Phase 2: Compose a single transaction that updates and/or creates nodes according to
            //          the above determinations (retry if there's an out-of-band modification)
            CuratorTransactionFinal transactionFinal = getTransaction(pathBytesMap, pathsWhichExist, parentPathsToCreate);
            if (i + 1 < ATOMIC_WRITE_ATTEMPTS) {
                try {
                    transactionFinal.commit();
                    break; // Success!
                } catch (Exception e) {
                    // Transaction failed! Bad connection? Existence check rendered invalid?
                    // Swallow exception and try again
                    logger.error(String.format("Failed to complete transaction attempt %d/%d: %s",
                            i + 1, ATOMIC_WRITE_ATTEMPTS, transactionFinal), e);
                }
            } else {
                // Last try: Any exception should be forwarded upstream
                transactionFinal.commit();
            }
        }
    }

    @Override
    public void set(String path, byte[] bytes) throws Exception {
        try {
            client.create().creatingParentsIfNeeded().forPath(path, bytes);
        } catch (KeeperException.NodeExistsException e) {
            client.setData().forPath(path, bytes);
        }
    }

    @Override
    public byte[] get(String path) throws Exception {
        return client.getData().forPath(path);
    }

    @Override
    public void delete(String path) throws Exception {
        client.delete().deletingChildrenIfNeeded().forPath(path);
    }

    @Override
    public Collection<String> getChildren(String path) throws Exception {
        return client.getChildren().forPath(path);
    }

    @Override
    public void close() {
        client.close();
    }

    private static CuratorFramework createClient(String connectionString, RetryPolicy retryPolicy) {
        CuratorFramework client = CuratorFrameworkFactory.newClient(connectionString, retryPolicy);
        client.start();
        return client;
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

    private List<String> getParentPathsToCreate(Set<String> paths, Set<String> pathsWhichExist)
            throws Exception {
        List<String> parentPathsToCreate = new ArrayList<>();
        for (String path : paths) {
            if (pathsWhichExist.contains(path)) {
                continue;
            }
            // Transaction interface doesn't support creatingParentsIfNeeded(), so go manual.
            for (String parentPath : CuratorUtils.getParentPaths(path)) {
                if (client.checkExists().forPath(parentPath) == null
                        && !parentPathsToCreate.contains(parentPath)) {
                    parentPathsToCreate.add(parentPath);
                }
            }
        }
        return parentPathsToCreate;
    }

    private CuratorTransactionFinal getTransaction(
            Map<String, byte[]> pathBytesMap,
            Set<String> pathsWhichExist,
            List<String> parentPathsToCreate) throws Exception {
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
}
