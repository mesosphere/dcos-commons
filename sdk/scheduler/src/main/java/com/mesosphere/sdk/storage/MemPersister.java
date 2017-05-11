package com.mesosphere.sdk.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.common.base.Splitter;
import com.mesosphere.sdk.state.PathUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;

/**
 * Implementation of {@link Persister} which stores the data in local memory. May be optionally configured to be
 * thread-safe.
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
     * Creates a new instance with locking enabled.
     */
    public MemPersister() {
        this(LockMode.ENABLED);
    }

    /**
     * Creates a new instance with locking disabled, for use in cases where the caller is already thread-safe.
     *
     * @see PersisterCache
     */
    public MemPersister(LockMode mode) {
        this.root = new Node();
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
            // iterate down the tree:
            Node node = getNode(path, false);
            if (node == null || !node.data.isPresent()) {
                throw new PersisterException(Reason.NOT_FOUND, path);
            }
            return node.data.get();
        } finally {
            unlockR();
        }
    }

    @Override
    public Collection<String> getChildren(String path) throws PersisterException {
        lockR();
        try {
            Node node = getNode(path, false);
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
            getNode(path, true).data = Optional.of(bytes);
        } finally {
            unlockRW();
        }
    }

    @Override
    public void setMany(Map<String, byte[]> pathBytesMap) throws PersisterException {
        lockRW();
        try {
            for (Map.Entry<String, byte[]> entry : pathBytesMap.entrySet()) {
                getNode(entry.getKey(), true).data = Optional.of(entry.getValue());
            }
        } finally {
            unlockRW();
        }
    }

    @Override
    public void delete(String path) throws PersisterException {
        List<String> elements = new ArrayList<>();
        elements.addAll(getPathElements(path));
        if (elements.isEmpty()) {
            close(); // treat as a reset
        }
        String nodeName = elements.get(elements.size() - 1);
        elements.remove(elements.size() - 1);

        lockRW();
        try {
            Node parent = getNode(elements, false);
            if (parent == null) {
                // Parent node didn't exist.
                throw new PersisterException(Reason.NOT_FOUND, path);
            }

            if (parent.children.remove(nodeName) == null) {
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

    private Node getNode(String path, boolean createIfMissing) {
        return getNode(getPathElements(path), createIfMissing);
    }

    private Node getNode(List<String> pathElements, boolean createIfMissing) {
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
        return Splitter.on(PathUtils.PATH_DELIM).omitEmptyStrings().splitToList(path);
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
}
