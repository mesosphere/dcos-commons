package com.mesosphere.sdk.kafka.upgrade;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.evaluate.placement.RegexMatcher;
import com.mesosphere.sdk.storage.MemPersister;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.SlaveID;
import org.junit.Before;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/**
 * Tests to validate the operation of the {@link FilterStateStore}.
 */
public class FilterStateStoreTest {

    private static final Protos.TaskState TASK_STATE = Protos.TaskState.TASK_STAGING;
    private FilterStateStore store;

    @Before
    public void beforeEach() throws Exception {
        store = new FilterStateStore(new MemPersister());
    }

    @Test
    public void testFetchTask() throws Exception {
        String testTaskNamePrefix = "test-executor";
        String testTaskName0 = testTaskNamePrefix + "-0";
        String testTaskName1 = testTaskNamePrefix + "-1";

        store.storeTasks(createTasks(testTaskName0, testTaskName1));
        assertEquals(Arrays.asList(testTaskName0, testTaskName1), store.fetchTaskNames());
        Collection<Protos.TaskInfo> taskInfos = store.fetchTasks();
        assertEquals(2, taskInfos.size());
        Iterator<Protos.TaskInfo> iter2 = taskInfos.iterator();
        assertEquals(testTaskName0, iter2.next().getName());
        assertEquals(testTaskName1, iter2.next().getName());

        store.setIgnoreFilter(RegexMatcher.create("test-executor-[0-9]*"));
        assertTrue(store.fetchTaskNames().isEmpty());
        taskInfos = store.fetchTasks();
        assertEquals(0, taskInfos.size());

        testTaskNamePrefix = "test1-executor";
        String testTaskName00 = testTaskNamePrefix + "-0";
        String testTaskName11 = testTaskNamePrefix + "-1";

        store.storeTasks(createTasks(testTaskName00, testTaskName11));
        assertEquals(Arrays.asList(testTaskName00, testTaskName11), store.fetchTaskNames());
        taskInfos = store.fetchTasks();
        assertEquals(2, taskInfos.size());

        store.setIgnoreFilter(RegexMatcher.create("z*"));
        assertEquals(Arrays.asList(testTaskName0, testTaskName1, testTaskName00, testTaskName11),
                store.fetchTaskNames());
        taskInfos = store.fetchTasks();
        assertEquals(4, taskInfos.size());

        store.setIgnoreFilter(null);
        assertEquals(Arrays.asList(testTaskName0, testTaskName1, testTaskName00, testTaskName11),
                store.fetchTaskNames());
        taskInfos = store.fetchTasks();
        assertEquals(4, taskInfos.size());
    }

    @Test
    public void testFetchStatuses() throws Exception {
        String testTaskNamePrefix = "test-executor";
        String testTaskName0 = testTaskNamePrefix + "-0";
        String testTaskName1 = testTaskNamePrefix + "-1";

        store.storeTasks(createTasks(testTaskName0, testTaskName1));
        createTaskStatuses(testTaskName0, testTaskName1).stream().forEach(status -> store.storeStatus(status));
        Collection<Protos.TaskStatus> taskStatuses = store.fetchStatuses();
        Collection<Protos.TaskInfo> taskInfos = store.fetchTasks();
        assertEquals(taskInfos.toString(), 2, taskInfos.size());
        assertEquals(2, taskStatuses.size());

        store.setIgnoreFilter(RegexMatcher.create("test-executor-[0-9]*"));
        taskStatuses = store.fetchStatuses();
        taskInfos = store.fetchTasks();
        assertEquals(0, taskInfos.size());
        assertEquals(0, taskStatuses.size());

        testTaskNamePrefix = "test1-executor";
        testTaskName0 = testTaskNamePrefix + "-0";
        testTaskName1 = testTaskNamePrefix + "-1";

        store.storeTasks(createTasks(testTaskName0, testTaskName1));
        createTaskStatuses(testTaskName0, testTaskName1).stream().forEach(status -> store.storeStatus(status));
        taskStatuses = store.fetchStatuses();
        taskInfos = store.fetchTasks();
        assertEquals(2, taskInfos.size());
        assertEquals(2, taskStatuses.size());

        store.setIgnoreFilter(RegexMatcher.create("z*"));
        taskStatuses = store.fetchStatuses();
        taskInfos = store.fetchTasks();
        assertEquals(4, taskInfos.size());
        assertEquals(4, taskStatuses.size());

        store.setIgnoreFilter(null);
        taskStatuses = store.fetchStatuses();
        taskInfos = store.fetchTasks();
        assertEquals(4, taskInfos.size());
        assertEquals(4, taskStatuses.size());
    }

    private static Collection<Protos.TaskInfo> createTasks(String... taskNames) {
        Collection<Protos.TaskInfo> taskInfos = new ArrayList<>();
        for (String taskName : taskNames) {
            taskInfos.add(Protos.TaskInfo.newBuilder()
                    .setName(taskName)
                    .setTaskId(CommonIdUtils.toTaskId(taskName))
                    .setSlaveId(SlaveID.newBuilder().setValue("ignored"))
                    .build());
        }
        return taskInfos;
    }

    private Collection<Protos.TaskStatus> createTaskStatuses(String... taskNames) {
        Collection<Protos.TaskStatus> taskStatuses = new ArrayList<>();
        for (String taskName : taskNames) {
            taskStatuses.add(createTaskStatus(store.fetchTask(taskName).get().getTaskId(), taskName));
        }
        return taskStatuses;
    }

    private static Protos.TaskStatus createTaskStatus(Protos.TaskID taskId, String taskName) {
        return Protos.TaskStatus.newBuilder()
                .setTaskId(CommonIdUtils.toTaskId(taskName))
                .setState(TASK_STATE)
                .setTaskId(taskId).build();
    }
}
