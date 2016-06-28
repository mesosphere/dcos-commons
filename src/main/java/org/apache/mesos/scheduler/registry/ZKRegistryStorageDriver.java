package org.apache.mesos.scheduler.registry;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.apache.curator.framework.CuratorFramework;
import org.apache.mesos.scheduler.txnplan.SerializationUtil;
import org.apache.zookeeper.KeeperException;

import java.util.*;

/**
 * Created by dgrnbrg on 6/28/16.
 */
public class ZKRegistryStorageDriver implements RegistryStorageDriver {
    private CuratorFramework curator;

    /**
     * Creates an implementation of PlanStorageDriver
     * @param curator
     */
    public ZKRegistryStorageDriver(CuratorFramework curator) {
        this.curator = curator;
    }

    private void storeData(String path, byte[] data) throws Exception {
        try {
            curator.create().creatingParentsIfNeeded().forPath(path, data);
        } catch (KeeperException.NodeExistsException e) {
            curator.setData().forPath(path, data);
        }
    }

    @Override
    public void storeTask(Task task) {
        try {
            Output output = new Output(10*4096);
            SerializationUtil.kryos.get().writeObject(output, task);
            byte[] data = output.toBytes();
            storeData("/registry/tasks/" + task.getName(), data);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to store task " + task.getName(), t);
        }
    }

    @Override
    public void deleteTask(String name) {
        try {
            curator.delete().forPath("/registry/tasks/" + name);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to delete task " + name, t);
        }
    }

    @Override
    public Collection<Task> loadAllTasks() {
        try {
            List<Task> tasks = new ArrayList<>();
            for (String child : curator.getChildren().forPath("/registry/tasks")) {
                Input input = new Input(curator.getData().forPath("/registry/tasks/" + child));
                Task task = SerializationUtil.kryos.get().readObject(input, Task.class);
                tasks.add(task);
            }
            return tasks;
        } catch (KeeperException.NoNodeException e) {
            return Collections.EMPTY_LIST;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to load tasks", t);
        }
    }

}
