package com.mesosphere.sdk.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import com.google.common.base.Splitter;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.state.CycleDetectingLockUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;

/**
 * Implementation of {@link Persister} which stores the data in local memory. Mirrors the behavior of
 * {@link com.mesosphere.sdk.curator.CuratorPersister}.
 */
public class MemPersister implements Persister {

    private static class Node {
        private final Map<String, Node> children;
        private Optional<byte[]> data;

        private Node() {
            this.children = new TreeMap<>();
            this.data = Optional.empty();
        }
    }

    // We use a tree structure to simplify getChildren() and (recursive) delete():
    private final Node root;

    private final Optional<Lock> rlock;
    private final Optional<Lock> rwlock;

    /**
     * Whether to enable or disable thread-safe locking.
     * Locking may be disabled if the parent implements its own locking.
     */
    public enum LockMode {
        ENABLED,
        DISABLED
    }

    /**
     * Returns a new {@link Builder} instance with the following default configuration:
     *
     * <ul><li>Locking enabled</li>
     * <li>Exit if there's a deadlock</li>
     * <li>No initial data</li></ul>
     *
     * Any of these settings may be changed via the returned {@link Builder}.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for {@link MemPersister}s.
     */
    public static class Builder {
        private LockMode lockMode = LockMode.ENABLED;
        private boolean exitOnDeadlock = true;
        private Map<String, byte[]> initialData = Collections.emptyMap();

        private Builder() {
        }

        /**
         * Configures whether any deadlocks should result in exiting the scheduler process. By default, if this is not
         * called then exiting on deadlock is enabled.
         *
         * @return this
         */
        public Builder configureExitOnDeadlock(SchedulerConfig schedulerConfig) {
            this.exitOnDeadlock = schedulerConfig.isDeadlockExitEnabled();
            return this;
        }

        /**
         * Disables thread locking. This should only be invoked by {@link PersisterCache} which has its own locking.
         * Calling this also implies disabling the exit-on-deadlock feature.
         *
         * @return this
         */
        Builder disableLocking() {
            this.lockMode = LockMode.DISABLED;
            this.exitOnDeadlock = false;
            return this;
        }

        /**
         * Assigns some initial data to be stored in the created instance. This should only be invoked by
         * {@link PersisterCache} which may have previous data to be reloaded.
         *
         * @param data a mapping of path to raw data
         * @return this
         */
        Builder setData(Map<String, byte[]> data) {
            this.initialData = data;
            return this;
        }

        public MemPersister build() {
            return new MemPersister(lockMode, exitOnDeadlock, initialData);
        }
    }

    /**
     * Creates a new instance with the provided options. See {@link Builder}.
     */
    private MemPersister(LockMode mode, boolean exitOnDeadlock, Map<String, byte[]> data) {
        this.root = new Node();
        for (Map.Entry<String, byte[]> entry : data.entrySet()) {
            getNode(root, entry.getKey(), true).data = Optional.of(entry.getValue());
        }
        if (mode == LockMode.ENABLED) {
            ReadWriteLock lock = CycleDetectingLockUtils.newLock(exitOnDeadlock, MemPersister.class);
            this.rlock = Optional.of(lock.readLock());
            this.rwlock = Optional.of(lock.writeLock());
        } else {
            this.rlock = Optional.empty();
            this.rwlock = Optional.empty();
        }
    }

    @Override
    public byte[] get(String path) throws PersisterException {
        lockR();
        try {
            Node node = getNode(root, path, false);
            if (node == null) {
                throw new PersisterException(Reason.NOT_FOUND, path); // node not found at all
            }
            return node.data.orElse(null); // support case where node exists but doesn't have data
        } finally {
            unlockR();
        }
    }

    @Override
    public Collection<String> getChildren(String path) throws PersisterException {
        lockR();
        try {
            Node node = getNode(root, path, false);
            if (node == null) {
                throw new PersisterException(Reason.NOT_FOUND, path);
            }
            return new TreeSet<>(node.children.keySet()); // return consistent ordering (mainly to simplify testing)
        } finally {
            unlockR();
        }
    }

    @Override
    public void set(String path, byte[] bytes) throws PersisterException {
        lockRW();
        try {
            getNode(root, path, true).data = Optional.of(bytes);
        } finally {
            unlockRW();
        }
    }

    @Override
    public void setMany(Map<String, byte[]> pathBytesMap) throws PersisterException {
        if (pathBytesMap.isEmpty()) {
            return;
        }
        lockRW();
        try {
            for (Map.Entry<String, byte[]> entry : pathBytesMap.entrySet()) {
                getNode(root, entry.getKey(), true).data = Optional.of(entry.getValue());
            }
        } finally {
            unlockRW();
        }
    }

    @Override
    public void recursiveDeleteMany(Collection<String> paths) throws PersisterException {
        lockRW();
        try {
            for (String path: paths) {
                deleteAllImpl(path);
            }
        } finally {
            unlockRW();
        }
    }

    @Override
    public Map<String, byte[]> getMany(Collection<String> paths) throws PersisterException {
        lockR();
        try {
            Map<String, byte[]> values = new TreeMap<>(); // return consistent ordering (mainly to simplify testing)
            for (String path : paths) {
                Node node = getNode(root, path, false);
                if (node == null) {
                    values.put(path, null);
                } else {
                    values.put(path, node.data.orElse(null));
                }
            }
            return values;
        } finally {
            unlockR();
        }
    }

    @Override
    public void recursiveDelete(String path) throws PersisterException {
        lockRW();
        try {
            if (!deleteAllImpl(path)) {
                throw new PersisterException(Reason.NOT_FOUND, path);
            }
        } finally {
            unlockRW();
        }
    }

    @Override
    public void close() {
        lockRW();
        try {
            root.children.clear();
            root.data = Optional.empty();
        } finally {
            unlockRW();
        }
    }

    /**
     * Returns a summary of this instance in a newline-separated string.
     */
    public String getDebugString() {
        StringBuilder sb = new StringBuilder();
        nodeContent(sb, "ROOT", root, 1);
        return sb.toString();
    }

    /**
     * Deletes the entry if present. Returns whether the entry to be removed was found.
     *
     * <p>Note: Caller must obtain a read-write lock before invoking this method.
     */
    private boolean deleteAllImpl(String path) throws PersisterException {
        List<String> elements = new ArrayList<>();
        elements.addAll(getPathElements(path)); // make editable version
        if (elements.isEmpty()) {
            // treat this as a reset operation:
            close();
            return true;
        }
        String nodeName = elements.remove(elements.size() - 1);

        Node parent = getNode(root, elements, false);
        if (parent == null) {
            // Parent node didn't exist.
            return false;
        }

        Node removed = parent.children.remove(nodeName);
        // Return whether node to be removed existed.
        return removed != null;
    }

    private void lockRW() {
        if (rwlock.isPresent()) {
            rwlock.get().lock();
        }
    }

    private void unlockRW() {
        if (rwlock.isPresent()) {
            rwlock.get().unlock();
        }
    }

    private void lockR() {
        if (rlock.isPresent()) {
            rlock.get().lock();
        }
    }

    private void unlockR() {
        if (rlock.isPresent()) {
            rlock.get().unlock();
        }
    }

    private static void nodeContent(StringBuilder sb, String name, Node node, int level) {
        for (int i = 0; i < level; ++i) {
            sb.append("  ");
        }
        sb.append(name);
        sb.append(": ");
        sb.append(getInfo(node.data.orElse(null)));
        for (Map.Entry<String, Node> child : node.children.entrySet()) {
            sb.append('\n');
            nodeContent(sb, child.getKey(), child.getValue(), level + 1); // RECURSE
        }
    }

    private static String getInfo(byte[] bytes) {
        if (bytes == null) {
            return "NULL";
        } else if (bytes.length == 1) {
            return "1 byte";
        } else {
            return String.format("%d bytes", bytes.length);
        }
    }

    private static Node getNode(Node root, String path, boolean createIfMissing) {
        return getNode(root, getPathElements(path), createIfMissing);
    }

    private static Node getNode(Node root, List<String> pathElements, boolean createIfMissing) {
        Node lastNode = null;
        Node curNode = root;
        for (String element : pathElements) {
            lastNode = curNode;
            curNode = curNode.children.get(element);
            if (curNode == null) {
                if (createIfMissing) {
                    curNode = new Node();
                    lastNode.children.put(element, curNode);
                } else {
                    return null;
                }
            }
        }
        return curNode;
    }

    private static List<String> getPathElements(String path) {
        // use this instead of String.split(): avoid problems with paths that look like regexes
        return Splitter.on(PersisterUtils.PATH_DELIM).omitEmptyStrings().splitToList(path);
    }
}
