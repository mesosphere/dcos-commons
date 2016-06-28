package org.apache.mesos.scheduler.registry;

import java.util.Collection;

/**
 * Created by dgrnbrg on 6/20/16.
 */
public interface RegistryStorageDriver {
    void storeTask(Task task);

    void deleteTask(String name);

    Collection<Task> loadAllTasks();

}
