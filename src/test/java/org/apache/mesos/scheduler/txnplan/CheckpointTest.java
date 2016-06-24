package org.apache.mesos.scheduler.txnplan;

import org.apache.curator.test.TestingServer;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static junit.framework.TestCase.assertTrue;
import static org.apache.mesos.scheduler.txnplan.PlanTest.*;
import static org.testng.AssertJUnit.assertEquals;

/**
 * Created by dgrnbrg on 6/24/16.
 */
public class CheckpointTest {
    private TestingServer zkServer;

    public CheckpointTest() throws Exception {
        zkServer = new TestingServer(true);
    }

    @Test
    /**
     * Tests whether the plan interference tracker can run 3 plans with a bit of overlap
     */
    public void testPlanExecutorSerializer() throws InterruptedException {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        PlanExecutor executor = new PlanExecutor(null, new MockOperationDriverFactory(),
                new ZKPlanStorageDriver(zkServer.getConnectString(), UUID.randomUUID().toString()));

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
                new ZKPlanStorageDriver(zkServer.getConnectString(), UUID.randomUUID().toString()));

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
    public void stop() {
        try {
            zkServer.stop();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
