package org.apache.mesos.state;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.curator.CuratorStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * Thread-safe caching layer for an underlying {@link StateStore}.
 *
 * Writes are automatically forwarded to the underlying instance, while reads prioritize the local
 * instance. In order to maintain consistency, there should only be one StateStoreCache object per
 * process. In practice this works because there should only be one scheduler task/process
 * accessing the state data at any given time.
 *
 * Implementation note: All write operations always invoke the underlying storage before updating
 * the local cache. This avoids creating an inconsistent cache state if writing to the underlying
 * persistent store fails.
 */
public class StateStoreCache implements StateStore {

    private static final Logger logger = LoggerFactory.getLogger(CuratorStateStore.class);

    private static final ReadWriteLock _lock = new ReentrantReadWriteLock();
    protected static final Lock RLOCK = _lock.readLock();
    protected static final Lock RWLOCK = _lock.writeLock();

    private static StateStoreCache instance = null;

    protected final StateStore store;

    protected Optional<FrameworkID> frameworkId;

    protected Map<String, TaskID> nameToId = new HashMap<>();
    protected Map<TaskID, TaskInfo> idToTask = new HashMap<>();
    protected Map<TaskID, TaskStatus> idToStatus = new HashMap<>();

    protected Map<String, byte[]> properties = new HashMap<>();

    /**
     * Returns a cache instance. To ensure consistency, only one singleton cache instance may exist
     * in the process at a time. This function may be called multiple times, but only if the same
     * {@link StateStore} instance is provided each time.
     */
    public static StateStore getInstance(StateStore store) {
        RWLOCK.lock();
        try {
            if (instance == null) {
                instance = new StateStoreCache(store);
            } else if (instance.store != store) {
                // Disallow subsequent calls to getInstance() with different instances of StateStore.
                throw new IllegalStateException(String.format(
                        "StateStoreCache may only be used against a single instance of StateStore. " +
                        "got[%s] expected[%s]", store, instance.store));
            }
            return instance;
        } finally {
            RWLOCK.unlock();
        }
    }

    @VisibleForTesting
    StateStoreCache(StateStore store) throws StateStoreException {
        this.store = store;

        // Use bulk fetches to initialize cache with underlying storage state:
        frameworkId = store.fetchFrameworkId();
        for (TaskInfo task : store.fetchTasks()) {
            nameToId.put(task.getName(), task.getTaskId());
            idToTask.put(task.getTaskId(), task);
        }
        for (TaskStatus status : store.fetchStatuses()) {
            idToStatus.put(status.getTaskId(), status);
        }
        for (String key : store.fetchPropertyKeys()) {
            properties.put(key, store.fetchProperty(key));
        }
    }

    @Override
    public void storeFrameworkId(FrameworkID fwkId) throws StateStoreException {
        RWLOCK.lock();
        try {
            store.storeFrameworkId(fwkId);
            frameworkId = Optional.of(fwkId);
        } finally {
            RWLOCK.unlock();
        }
    }

    @Override
    public void clearFrameworkId() throws StateStoreException {
        RWLOCK.lock();
        try {
            store.clearFrameworkId();
            frameworkId = Optional.empty();
        } finally {
            RWLOCK.unlock();
        }
    }

    @Override
    public Optional<FrameworkID> fetchFrameworkId() throws StateStoreException {
        RLOCK.lock();
        try {
            return frameworkId;
        } finally {
            RLOCK.unlock();
        }
    }

    @Override
    public void storeTasks(Collection<TaskInfo> tasks) throws StateStoreException {
        RWLOCK.lock();
        try {
            store.storeTasks(tasks);
            for (TaskInfo task : tasks) {
                nameToId.put(task.getName(), task.getTaskId());
                idToTask.put(task.getTaskId(), task);
            }
        } finally {
            RWLOCK.unlock();
        }
    }

    @Override
    public void storeStatus(TaskStatus status) throws StateStoreException {
        RWLOCK.lock();
        try {
            store.storeStatus(status);
            // corner case: only store status if a name=>id mapping already exists from storing taskinfo.
            // this shouldn't come up in practice since the underlying statestore should have thrown anyway.
            if (!idToTask.containsKey(status.getTaskId())) {
                throw new StateStoreException(String.format(
                        "The following TaskInfo is not present in the StateStore: %s. " +
                        "TaskInfo must be present in order to store a TaskStatus.", status.getTaskId()));
            }
            idToStatus.put(status.getTaskId(), status);
        } finally {
            RWLOCK.unlock();
        }
    }

    @Override
    public void clearTask(String taskName) throws StateStoreException {
        RWLOCK.lock();
        try {
            store.clearTask(taskName);
            TaskID taskId = nameToId.remove(taskName);
            if (taskId != null) {
                idToTask.remove(taskId);
                idToStatus.remove(taskId);
            } else {
                logger.warn("Unable to find task named {} to remove. Known task names are: {}",
                        taskName, nameToId.keySet());
            }
        } finally {
            RWLOCK.unlock();
        }
    }

    @Override
    public Collection<String> fetchTaskNames() throws StateStoreException {
        RLOCK.lock();
        try {
            return nameToId.keySet();
        } finally {
            RLOCK.unlock();
        }
    }

    @Override
    public Collection<TaskInfo> fetchTasks() throws StateStoreException {
        RLOCK.lock();
        try {
            return idToTask.values();
        } finally {
            RLOCK.unlock();
        }
    }

    @Override
    public Optional<TaskInfo> fetchTask(String taskName) throws StateStoreException {
        RLOCK.lock();
        try {
            TaskID taskId = nameToId.get(taskName);
            if (taskId == null) {
                // Entry not found.
                return Optional.empty();
            }
            TaskInfo task = idToTask.get(taskId);
            if (task == null) {
                // If we have a name->ID mapping, we really should have a TaskInfo for that ID.
                throw new StateStoreException(String.format(
                        "Cache consistency error: Unable to find TaskInfo for known ID '%s'. Name=>ID[%s] TaskIDs[%s]",
                        taskName, nameToId, idToTask.keySet()));
            }
            return Optional.of(task);
        } finally {
            RLOCK.unlock();
        }
    }

    @Override
    public Collection<TaskStatus> fetchStatuses() throws StateStoreException {
        RLOCK.lock();
        try {
            return idToStatus.values();
        } finally {
            RLOCK.unlock();
        }
    }

    @Override
    public Optional<TaskStatus> fetchStatus(String taskName) throws StateStoreException {
        RLOCK.lock();
        try {
            TaskID taskId = nameToId.get(taskName);
            if (taskId == null) {
                // Task name doesn't exist at all.
                return Optional.empty();
            }
            TaskStatus status = idToStatus.get(taskId);
            if (status == null) {
                // Task name exists, but no status (storeTask was called but not yet storeStatus)
                return Optional.empty();
            }
            return Optional.of(status);
        } finally {
            RLOCK.unlock();
        }
    }

    @Override
    public void storeProperty(String key, byte[] value) throws StateStoreException {
        RWLOCK.lock();
        try {
            store.storeProperty(key, value);
            properties.put(key, value);
        } finally {
            RWLOCK.unlock();
        }
    }

    @Override
    public byte[] fetchProperty(String key) throws StateStoreException {
        RLOCK.lock();
        try {
            byte[] val = properties.get(key);
            if (val == null) { // emulate StateStore contract
                throw new StateStoreException(String.format(
                        "Property key does not exist: %s (known keys are: %s)",
                        key, properties.keySet()));
            }
            return val;
        } finally {
            RLOCK.unlock();
        }
    }

    @Override
    public Collection<String> fetchPropertyKeys() throws StateStoreException {
        RLOCK.lock();
        try {
            return properties.keySet();
        } finally {
            RLOCK.unlock();
        }
    }

    @Override
    public void clearProperty(String key) throws StateStoreException {
        RWLOCK.lock();
        try {
            store.clearProperty(key);
            properties.remove(key);
        } finally {
            RWLOCK.unlock();
        }
    }
}
