package com.mesosphere.sdk.storage;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mesosphere.sdk.state.PathUtils;

/**
 * A transparent write-through cache for an underlying {@link Persister} instance. Each cache instance is thread-safe,
 * but there is no guarantee of consistent behavior across multiple cache instances.
 */
public class PersisterCache implements Persister {

    private static final Logger logger = LoggerFactory.getLogger(PersisterCache.class);

    private final ReadWriteLock internalLock = new ReentrantReadWriteLock();
    private final Lock rlock = internalLock.readLock();
    private final Lock rwlock = internalLock.writeLock();

    private final Persister persister;
    private MemPersister cache;

    public PersisterCache(Persister persister) throws PersisterException {
        this.persister = persister;
        refresh();
    }

    @Override
    public byte[] get(String path) throws PersisterException {
        rlock.lock();
        try {
            return cache.get(path);
        } finally {
            rlock.unlock();
        }
    }

    @Override
    public Collection<String> getChildren(String path) throws PersisterException {
        rlock.lock();
        try {
            return cache.getChildren(path);
        } finally {
            rlock.unlock();
        }
    }

    @Override
    public void set(String path, byte[] bytes) throws PersisterException {
        rwlock.lock();
        try {
            persister.set(path, bytes);
            cache.set(path, bytes);
        } finally {
            rwlock.unlock();
        }
    }

    @Override
    public void setMany(Map<String, byte[]> pathBytesMap) throws PersisterException {
        rwlock.lock();
        try {
            persister.setMany(pathBytesMap);
            cache.setMany(pathBytesMap);
        } finally {
            rwlock.unlock();
        }
    }

    @Override
    public void delete(String path) throws PersisterException {
        rwlock.lock();
        try {
            persister.delete(path);
            try {
                cache.delete(path);
            } catch (PersisterException e) {
                // We don't throw an exception here if our 'data' cache lacks the value. In theory 'persister' should've
                // thrown in that case anyway -- so we're effectively replicating what the underlying persister does.
                // This shouldn't happen assuming a well-behaved Persisters, but just in case...
                logger.error("Didn't find value {} in cache to delete, but underlying storage had the value", path);
            }
        } finally {
            rwlock.unlock();
        }
    }

    @Override
    public void close() {
        rwlock.lock();
        try {
            persister.close();
            cache.close();
        } finally {
            rwlock.unlock();
        }
    }

    /**
     * Refreshes the cache with the underlying persister's data.
     */
    public void refresh() throws PersisterException {
        rwlock.lock();
        try {
            // We already have our own locking, so we don't need it in the underlying cache:
            cache = new MemPersister(MemPersister.LockMode.DISABLED);
            for (String key : persister.getChildren(PathUtils.PATH_DELIM)) {
                cache.set(key, persister.get(key));
            }
        } finally {
            rwlock.unlock();
        }
    }
}
