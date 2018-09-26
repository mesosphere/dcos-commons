package com.mesosphere.sdk.storage;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import org.slf4j.Logger;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.state.CycleDetectingLockUtils;

/**
 * A transparent write-through cache for an underlying {@link Persister} instance. Each cache instance is thread-safe,
 * but there is no guarantee of consistent behavior across multiple cache instances.
 */
public class PersisterCache implements Persister {

    private static final Logger LOGGER = LoggingUtils.getLogger(PersisterCache.class);

    private final Persister persister;
    private final Lock rlock;
    private final Lock rwlock;

    private MemPersister cache;

    public PersisterCache(Persister persister, SchedulerConfig schedulerConfig) throws PersisterException {
        this.persister = persister;
        ReadWriteLock lock = CycleDetectingLockUtils.newLock(schedulerConfig, PersisterCache.class);
        this.rlock = lock.readLock();
        this.rwlock = lock.writeLock();
    }

    @Override
    public byte[] get(String path) throws PersisterException {
        rlock.lock();
        try {
            return getCache().get(path);
        } finally {
            rlock.unlock();
        }
    }

    @Override
    public Collection<String> getChildren(String path) throws PersisterException {
        rlock.lock();
        try {
            return getCache().getChildren(path);
        } finally {
            rlock.unlock();
        }
    }

    @Override
    public void set(String path, byte[] bytes) throws PersisterException {
        rwlock.lock();
        try {
            MemPersister cache = getCache();
            // Optimization: Only update persister if new value != current cached value
            byte[] currentValue = cache.get(path);
            if (!Arrays.equals(currentValue, bytes)) {
                persister.set(path, bytes);
                cache.set(path, bytes);
            }
        } finally {
            rwlock.unlock();
        }
    }

    @Override
    public Map<String, byte[]> getMany(Collection<String> paths) throws PersisterException {
        rwlock.lock();
        try {
            return getCache().getMany(paths);
        } finally {
            rwlock.unlock();
        }
    }

    @Override
    public void setMany(Map<String, byte[]> pathBytesMap) throws PersisterException {
        rwlock.lock();
        try {
            MemPersister cache = getCache();
            persister.setMany(pathBytesMap);
            cache.setMany(pathBytesMap);
        } finally {
            rwlock.unlock();
        }
    }

    @Override
    public void recursiveCopy(String srcPath, String destPath) throws PersisterException {
        rwlock.lock();
        try {
            MemPersister cache = getCache();
            persister.recursiveCopy(srcPath, destPath);
            cache.recursiveCopy(srcPath, destPath);
        } finally {
            rwlock.unlock();
        }
    }

    @Override
    public void recursiveDeleteMany(Collection<String> paths) throws PersisterException {
        rwlock.lock();
        try {
            MemPersister cache = getCache();
            persister.recursiveDeleteMany(paths);
            cache.recursiveDeleteMany(paths);
        } finally {
            rwlock.unlock();
        }
    }

    @Override
    public void recursiveDelete(String path) throws PersisterException {
        rwlock.lock();
        try {
            MemPersister cache = getCache();
            persister.recursiveDelete(path);
            try {
                cache.recursiveDelete(path);
            } catch (PersisterException e) {
                // We don't throw an exception here if our 'data' cache lacks the value. In theory 'persister' should've
                // thrown in that case anyway -- so we're effectively replicating what the underlying persister does.
                // This shouldn't happen assuming a well-behaved Persisters, but just in case...
                LOGGER.error("Didn't find value {} in cache to delete, but underlying storage had the value", path);
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
            if (cache != null) {
                cache.close();
            }
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
            if (cache != null) {
                LOGGER.info("Cache content before refresh:\n{}", cache.getDebugString());
            }
            cache = null;
            getCache(); // recreate cache
        } finally {
            rwlock.unlock();
        }
    }

    private MemPersister getCache() throws PersisterException {
        if (cache == null) {
            // We already have our own locking, so we can disable locking in the underlying MemPersister:
            cache = MemPersister.newBuilder()
                    .disableLocking()
                    .setData(PersisterUtils.getAllData(persister))
                    .build();
            LOGGER.info("Loaded data from persister:\n{}", cache.getDebugString());
        }
        return cache;
    }
}
