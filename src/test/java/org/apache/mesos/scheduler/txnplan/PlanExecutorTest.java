package org.apache.mesos.scheduler.txnplan;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.BoundedExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.scheduler.registry.TaskRegistry;
import org.apache.mesos.scheduler.txnplan.ops.UnconditionalOp;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

import static junit.framework.TestCase.assertTrue;
import static org.apache.mesos.scheduler.txnplan.PlanTest.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;

/**
 * Created by dgrnbrg on 6/24/16.
 */
public class PlanExecutorTest {
    private TestingServer zkServer;
    private CuratorFramework curator;

    public PlanExecutorTest() throws Exception {
        zkServer = new TestingServer(true);
        logMap = new HashMap<>();
        try {
            curator = CuratorFrameworkFactory.builder()
                    .connectString(zkServer.getConnectString())
                    .retryPolicy(new BoundedExponentialBackoffRetry(100, 120000, 10))
                    .build();
            curator.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    /**
     * Tests whether the plan interference tracker can run 3 plans with a bit of overlap
     */
    public void testPlanExecutorSerializer() throws InterruptedException {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        PlanExecutor executor = new PlanExecutor(null, new MockOperationDriverFactory(),
                new ZKPlanStorageDriver(curator.usingNamespace(UUID.randomUUID().toString())));

        Plan plan1 = new Plan();
        Step one = plan1.step(new LogOp(log, "one", 100, "A"));
        Step two = plan1.step(new LogOp(log, "two", 100, "B"));
        two.requires(one);

        Plan plan2 = new Plan();
        Step yi = plan2.step(new LogOp(log, "yi", 100, "A"));
        Step er = plan2.step(new LogOp(log, "er", 100, "C"));
        er.requires(yi);

        Plan plan3 = new Plan();
        Step odin = plan3.step(new LogOp(log, "odin", 100, "B"));
        Step dva = plan3.step(new LogOp(log, "dva", 100, "C"));
        dva.requires(odin);

        executor.submitPlan(plan1, Collections.EMPTY_LIST);
        executor.submitPlan(plan2, Collections.EMPTY_LIST);
        executor.submitPlan(plan3, Collections.EMPTY_LIST);

        Thread.sleep(1000);

        assertEquals(6, log.size());
        List<String> l1 = Arrays.asList(log.get(0), log.get(1));
        List<String> l2 = Arrays.asList(log.get(2), log.get(3));
        List<String> l3 = Arrays.asList(log.get(4), log.get(5));
        List<List<String>> goal = Arrays.asList(
                Arrays.asList("one", "two"),
                Arrays.asList("yi", "er"),
                Arrays.asList("odin", "dva"));

        assertTrue(goal.contains(l1));
        assertTrue(goal.contains(l2));
        assertTrue(goal.contains(l3));
    }
    @Test
    /**
     * Tests whether the plan interference tracker can run 3 plans without any overlap
     */
    public void testPlanExecutorConcurrent() throws InterruptedException {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        PlanExecutor executor = new PlanExecutor(null, new MockOperationDriverFactory(),
                new ZKPlanStorageDriver(curator.usingNamespace(UUID.randomUUID().toString())));

        Plan plan1 = new Plan();
        Step one = plan1.step(new LogOp(log, "one", 100, "A"));
        Step two = plan1.step(new LogOp(log, "two", 100, "A"));
        two.requires(one);

        Plan plan2 = new Plan();
        Step yi = plan2.step(new LogOp(log, "yi", 100, "B"));
        Step er = plan2.step(new LogOp(log, "er", 100, "B"));
        er.requires(yi);

        Plan plan3 = new Plan();
        Step odin = plan3.step(new LogOp(log, "odin", 100, "C"));
        Step dva = plan3.step(new LogOp(log, "dva", 100, "C"));
        dva.requires(odin);

        executor.submitPlan(plan1, Collections.EMPTY_LIST);
        executor.submitPlan(plan2, Collections.EMPTY_LIST);
        executor.submitPlan(plan3, Collections.EMPTY_LIST);

        Thread.sleep(500);

        assertEquals(6, log.size());
        List<String> l1 = Arrays.asList(log.get(0), log.get(1), log.get(2));
        List<String> l2 = Arrays.asList(log.get(3), log.get(4), log.get(5));
        assertEquals(new HashSet(Arrays.asList("one", "yi", "odin")), new HashSet(l1));
        assertEquals(new HashSet(Arrays.asList("two", "er", "dva")), new HashSet(l2));
    }

    @Test
    /**
     * This test attempts to launch a couple partially completed plans--one is half-done,
     * the other was submitted but never made it to the queue.
     *
     * We need to use an ugly static var hack to make it compatible with serialization
     *
     * We're creating 2 plans, each with 2 sequential steps.
     * The plans have no interference with each other.
     *
     * We're also setting the state of the world to have plan1 having finished step1 but not step2,
     * and plan2 having been stored but not even added to the queue yet.
     *
     * We expect that we'll see the remaining steps finish.
     */
    public void testReload() throws InterruptedException {
        List<String> logUS = Collections.synchronizedList(new ArrayList<>());
        List<String> logZH = Collections.synchronizedList(new ArrayList<>());
        logMap.put("us", logUS);
        logMap.put("zh", logZH);

        Plan plan1 = new Plan();
        Step one = plan1.step(new SerializableLogOp("us", "one", 100));
        Step two = plan1.step(new SerializableLogOp("us", "two", 100, "A"));
        two.requires(one);
        plan1.freeze();

        Plan plan2 = new Plan();
        Step yi = plan2.step(new SerializableLogOp("zh", "yi", 100, "B"));
        Step er = plan2.step(new SerializableLogOp("zh", "er", 100, "B"));
        er.requires(yi);
        plan2.freeze();

        PlanStatus plan1status = new PlanStatus(plan1)
                .startStep(one.getUuid())
                .finishStep(one.getUuid())
                .startStep(two.getUuid());

        Set<UUID> runningPlans = new HashSet<>(Arrays.asList(plan1.getUuid()));
        Map<String, Queue<UUID>> planQueue = new HashMap<>();
        Queue<UUID> queueForA = new ArrayDeque<>();
        queueForA.add(plan1.getUuid());
        planQueue.put("A", queueForA);
        Queue<UUID> queueForB = new ArrayDeque<>();
        queueForB.add(plan2.getUuid());
        planQueue.put("B", queueForB);

        PlanStorageDriver storageDriver = new ZKPlanStorageDriver(curator.usingNamespace(UUID.randomUUID().toString()));
        storageDriver.savePlan(plan1);
        storageDriver.savePlan(plan2);
        storageDriver.saveStatusForPlan(plan1status);
        storageDriver.saveSchedulerState(planQueue, runningPlans);

        PlanExecutor executor = new PlanExecutor(null, new MockOperationDriverFactory(), storageDriver);
        executor.reloadFromStorage();

        Thread.sleep(500);

        assertEquals(Arrays.asList("two"), logMap.get("us"));
        assertEquals(Arrays.asList("yi", "er"), logMap.get("zh"));
    }

    private static Map<String, List<String>> logMap;

    public static class SerializableLogOp extends UnconditionalOp {
        private String logId;
        private String msg;
        private long delay;

        private SerializableLogOp() {
            super(Collections.EMPTY_LIST);
        }

        public SerializableLogOp(String logId, String msg, long delay, String ... interferences) {
            super(Arrays.asList(interferences));
            this.logId = logId;
            this.msg = msg;
            this.delay = delay;
        }

        @Override
        public void doAction(TaskRegistry registry, OperationDriver driver) throws Exception {
            Thread.sleep(delay);
            logMap.get(logId).add(msg);
        }
    }

    public void stop() {
        try {
            zkServer.stop();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
