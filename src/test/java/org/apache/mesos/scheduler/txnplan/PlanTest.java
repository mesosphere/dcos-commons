package org.apache.mesos.scheduler.txnplan;

import org.apache.mesos.scheduler.registry.TaskRegistry;
import org.apache.mesos.scheduler.txnplan.ops.UnconditionalOp;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Created by dgrnbrg on 6/22/16.
 */
public class PlanTest {

    private void launchPlanAndWait(Plan plan, long ms) {
        ExecutorService service = Executors.newCachedThreadPool();
        PlanTracker tracker = new PlanTracker(plan, service, new MockOperationDriverFactory(), null, Collections.EMPTY_LIST);
        tracker.resumeExecution();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    /**
     * Tests whether the plan interference tracker can run 3 plans with a bit of overlap
     */
    public void testPlanExecutorSerializer() throws InterruptedException {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        PlanExecutor executor = new PlanExecutor(null, new MockOperationDriverFactory(), new InProcZKPlanStorageDriver());

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
        PlanExecutor executor = new PlanExecutor(null, new MockOperationDriverFactory(), new InProcZKPlanStorageDriver());

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
     * Tests whether ops are delayed until it's their turn;
     * this would return a list in the opposite order if they ran at the same time
     */
    public void testSimpleLinear() {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        Plan plan = new Plan();
        Step one = plan.step(new LogOp(log, "one", 150));
        Step two = plan.step(new LogOp(log, "two", 100));
        Step three = plan.step(new LogOp(log, "three", 50));
        //These requirements ensure that the plan runs in the correct order
        three.requires(two);
        two.requires(one);
        plan.freeze();
        launchPlanAndWait(plan, 500);
        assertEquals(Arrays.asList("one", "two", "three"), log);
    }

    @Test
    /**
     * Tests whether 3 independent ops start simultaneously
     */
    public void testSimpleParallel() {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        Plan plan = new Plan();
        Step one = plan.step(new LogOp(log, "one", 150));
        Step two = plan.step(new LogOp(log, "two", 100));
        Step three = plan.step(new LogOp(log, "three", 50));
        //Don't put requirements
        plan.freeze();
        launchPlanAndWait(plan, 500);
        assertEquals(Arrays.asList("three", "two", "one"), log);
    }


    @Test
    /**
     * Tests whether an exception in the action triggers a rollback
     */
    public void testRollback() {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        Plan plan = new Plan();
        Step one = plan.step(new RollbackOp(log, "one", false));
        Step two = plan.step(new RollbackOp(log, "two", true));
        //This sidecar should not have had a chance to log before the rollback
        Step sidecar = plan.step(new LogOp(log, "sidecar", 200));
        two.requires(one);
        plan.freeze();
        launchPlanAndWait(plan, 500);
        assertEquals(Arrays.asList("one", "two", "rollback two", "rollback one"), log);
    }

    @Test
    /**
     * Tests whether an exception in the action triggers a rollback
     */
    public void testAbort() {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        Plan plan = new Plan();
        Step one = plan.step(new RollbackOp(log, "one", false));
        Step two = plan.step(new RollbackOp(log, "from orbit", true));
        //This sidecar should not have had a chance to log before the rollback
        Step sidecar = plan.step(new LogOp(log, "sidecar", 500));
        two.requires(one);
        plan.freeze();
        launchPlanAndWait(plan, 500);
        assertEquals(Arrays.asList("one", "from orbit"), log);
    }


    public static class LogOp extends UnconditionalOp {
        private List<String> log;
        private String msg;
        private long delay;

        public LogOp(List<String> log, String msg, long delay, String ... interferences) {
            super(Arrays.asList(interferences));
            this.log = log;
            this.msg = msg;
            this.delay = delay;
        }

        public LogOp(List<String> log, String msg, long delay) {
            super(Collections.EMPTY_LIST); // empty interference set
            this.log = log;
            this.msg = msg;
            this.delay = delay;
        }

        @Override
        public void doAction(TaskRegistry registry, OperationDriver driver) throws Exception {
            Thread.sleep(delay);
            log.add(msg);
        }
    }

    public static class RollbackOp implements Operation {
        private List<String> log;
        private String id;
        private boolean willThrow;

        public RollbackOp(List<String> log, String id, boolean willThrow) {
            this.log = log;
            this.id = id;
            this.willThrow = willThrow;
        }

        @Override
        public void doAction(TaskRegistry registry, OperationDriver driver) throws Exception {
            log.add(id);
            if (willThrow) {
                throw new RuntimeException("it broke :)");
            }
        }

        @Override
        public void rollback(TaskRegistry registry, OperationDriver driver) throws Exception {
            if (id.equals("from orbit")) {
                throw new RuntimeException("blasted");
            }
            log.add("rollback " + id);
        }

        @Override
        public Collection<String> lockedExecutors() {
            return Collections.EMPTY_LIST;
        }
    }
}
