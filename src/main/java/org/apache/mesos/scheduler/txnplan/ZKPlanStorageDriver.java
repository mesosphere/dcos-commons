package org.apache.mesos.scheduler.txnplan;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;

import java.util.*;

/**
 * Created by dgrnbrg on 6/24/16.
 */
public class ZKPlanStorageDriver implements PlanStorageDriver {
    private CuratorFramework curator;

    /**
     * Creates an implementation of PlanStorageDriver
     * @param curator
     */
    public ZKPlanStorageDriver(CuratorFramework curator) {
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
    public void saveStatusForPlan(PlanStatus status) {
        try {
            Output output = new Output(10*4096);
            SerializationUtil.kryos.get().writeObject(output, status);
            byte[] data = output.toBytes();
            storeData("/statuses/" + status.getPlanUUID().toString(), data);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to save status for plan " + status.getPlanUUID(), t);
        }
    }

    @Override
    public void savePlan(Plan plan) {
        try {
            //TODO understand whether we can get away with a smaller buffer, or how to size this...
            Output output = new Output(10*4096);
            SerializationUtil.kryos.get().writeObject(output, plan);
            byte[] data = output.toBytes();
            storeData("/plans/" + plan.getUuid().toString(), data);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to save plan" + plan.getUuid(), t);
        }
    }

    @Override
    public void saveSchedulerState(Map<String, Queue<UUID>> planQueue, Set<UUID> runningPlans) {
        try {
            Output output = new Output(10*4096);
            SerializationUtil.kryos.get().writeClassAndObject(output, planQueue);
            SerializationUtil.kryos.get().writeClassAndObject(output, runningPlans);
            byte[] data = output.toBytes();
            storeData("/scheduler_state", data);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to save scheduler state", t);
        }
    }

    @Override
    public SchedulerState loadSchedulerState() {
        try {
            Input input = new Input(curator.getData().forPath("/scheduler_state"));
            Map<String, Queue<UUID>> planQueue = (Map) SerializationUtil.kryos.get().readClassAndObject(input);
            Set<UUID> runningPlans = (Set) SerializationUtil.kryos.get().readClassAndObject(input);
            return new SchedulerState(planQueue, runningPlans);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to load scheduler state", t);
        }
    }

    @Override
    public Map<UUID, Plan> loadPlans() {
        try {
            Map<UUID, Plan> plans = new HashMap<>();
            for (String child : curator.getChildren().forPath("/plans")) {
                Input input = new Input(curator.getData().forPath("/plans/" + child));
                Plan plan = SerializationUtil.kryos.get().readObject(input, Plan.class);
                plans.put(plan.getUuid(), plan);
            }
            return plans;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to load plans", t);
        }
    }

    @Override
    public PlanStatus tryLoadPlanStatus(UUID id) {
        try {
            Input input = new Input(curator.getData().forPath("/statuses/" + id.toString()));
            return SerializationUtil.kryos.get().readObject(input, PlanStatus.class);
        } catch (KeeperException.NoNodeException e) {
            return null;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to load plans", t);
        }
    }
}
