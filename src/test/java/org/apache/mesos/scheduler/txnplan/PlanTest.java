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
        public Collection<String> lockedTasks() {
            return Collections.EMPTY_LIST;
        }
    }
}
