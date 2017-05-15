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
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.common.base.Splitter;
import com.mesosphere.sdk.storage.StorageError.Reason;

/**
 * Implementation of {@link Persister} which stores the data in local memory. Mirrors the behavior of
 * {@link CuratorPersister}.
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

    private final Optional<ReadWriteLock> internalLock;
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
     * Creates a new instance with locking enabled and without any initial data.
     */
    public MemPersister() {
        this(LockMode.ENABLED, Collections.emptyMap());
    }

    /**
     * Creates a new instance with the provided options.
     *
     * @param mode Allows enabling or disabling internal thread-safe locking. Disable in cases where the caller is
     *     already performing their own locking
     * @param data The initial data to be stored in the instance, or an empty map if none is applicable
     */
    public MemPersister(LockMode mode, Map<String, byte[]> data) {
        this.root = new Node();
        for (Map.Entry<String, byte[]> entry : data.entrySet()) {
            getNode(root, entry.getKey(), true).data = Optional.of(entry.getValue());
        }
        if (mode == LockMode.ENABLED) {
            internalLock = Optional.of(new ReentrantReadWriteLock());
            rlock = Optional.of(internalLock.get().readLock());
            rwlock = Optional.of(internalLock.get().writeLock());
        } else {
            internalLock = Optional.empty();
            rlock = Optional.empty();
            rwlock = Optional.empty();
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
    public void deleteAll(String path) throws PersisterException {
        List<String> elements = new ArrayList<>();
        elements.addAll(getPathElements(path)); // make editable version
        if (elements.isEmpty()) {
            // treat this as a reset operation:
            close();
            return;
        }
        String nodeName = elements.remove(elements.size() - 1);

        lockRW();
        try {
            Node parent = getNode(root, elements, false);
            if (parent == null) {
                // Parent node didn't exist.
                throw new PersisterException(Reason.NOT_FOUND, path);
            }

            Node removed = parent.children.remove(nodeName);
            if (removed == null) {
                // Node to remove didn't exist.
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
        return bytes == null ? "NULL" : String.format("%d bytes", bytes.length);
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
