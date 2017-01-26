package com.mesosphere.sdk.state;

import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;
import com.mesosphere.sdk.testutils.CuratorTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.Charset;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link StateStoreCache}
 */
@SuppressWarnings("PMD.EmptyCatchBlock")
public class StateStoreCacheTest {
    private static final String ROOT_ZK_PATH = "/test-root-path";

    private static final FrameworkID FRAMEWORK_ID = FrameworkID.newBuilder().setValue("foo").build();
    private static final FrameworkID FRAMEWORK_ID2 = FrameworkID.newBuilder().setValue("foo2").build();

    private static final String PROP_KEY = "property";
    private static final byte[] PROP_VAL = "someval".getBytes(Charset.defaultCharset());
    private static final String PROP_KEY2 = "property2";
    private static final byte[] PROP_VAL2 = "someval2".getBytes(Charset.defaultCharset());

    private static final String TASK_NAME = "task";
    private static final TaskInfo TASK = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder()
            .setName(TASK_NAME)
            .setTaskId(CommonTaskUtils.toTaskId(TASK_NAME))
            .build();
    private static final TaskStatus STATUS = Protos.TaskStatus.newBuilder()
            .setTaskId(TASK.getTaskId())
            .setState(TaskState.TASK_KILLING)
            .build();
    private static final String TASK_NAME2 = "task2";
    private static final TaskInfo TASK2 = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder()
            .setName(TASK_NAME2)
            .setTaskId(CommonTaskUtils.toTaskId(TASK_NAME2))
            .build();
    private static final TaskStatus STATUS2 = Protos.TaskStatus.newBuilder()
            .setTaskId(TASK2.getTaskId())
            .setState(TaskState.TASK_ERROR)
            .build();

    private static TestingServer testZk;
    private StateStore store;
    private TestStateStoreCache cache;

    @Mock private StateStore mockStore;
    private StateStoreCache mockedCache;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        CuratorTestUtils.clear(testZk);
        store = new CuratorStateStore(ROOT_ZK_PATH, testZk.getConnectString());
        cache = new TestStateStoreCache(store);

        MockitoAnnotations.initMocks(this);
        when(mockStore.fetchFrameworkId()).thenReturn(Optional.empty());
        when(mockStore.fetchTasks()).thenReturn(Arrays.asList(TASK));
        when(mockStore.fetchStatuses()).thenReturn(Arrays.asList(STATUS));
        when(mockStore.fetchPropertyKeys()).thenReturn(Arrays.asList(PROP_KEY));
        when(mockStore.fetchProperty(PROP_KEY)).thenReturn(PROP_VAL);
        mockedCache = new StateStoreCache(mockStore);
    }

    @After
    public void afterEach() {
        ((CuratorStateStore) store).closeForTesting();
    }

    @Test(expected=IllegalStateException.class)
    public void testGetInstance() {
        StateStore instance = StateStoreCache.getInstance(mockStore);
        assertNotNull(instance);
        assertSame(instance, StateStoreCache.getInstance(mockStore));
        StateStoreCache.getInstance(store); // should throw
    }

    @Test(expected=StateStoreException.class)
    public void testMissingTaskInfoStartup() {
        when(mockStore.fetchTasks()).thenReturn(Arrays.asList(TASK));
        when(mockStore.fetchStatuses()).thenReturn(Arrays.asList(STATUS, STATUS2));
        mockedCache = new StateStoreCache(mockStore);
    }

    @Test
    public void testFrameworkIdSingleThread() {
        cache.consistencyCheckForTests();
        assertFalse(cache.fetchFrameworkId().isPresent());
        cache.storeFrameworkId(FRAMEWORK_ID);
        cache.consistencyCheckForTests();
        assertEquals(FRAMEWORK_ID, cache.fetchFrameworkId().get());
        cache.storeFrameworkId(FRAMEWORK_ID2);
        cache.consistencyCheckForTests();
        assertEquals(FRAMEWORK_ID2, cache.fetchFrameworkId().get());
        cache.clearFrameworkId();
        cache.consistencyCheckForTests();
        assertFalse(cache.fetchFrameworkId().isPresent());
    }

    @Test
    public void testFrameworkIdMultiThread() throws InterruptedException {
        runThreads(new Runnable() {
            @Override
            public void run() {
                cache.consistencyCheckForTests();
                cache.storeFrameworkId(FRAMEWORK_ID);
                cache.consistencyCheckForTests();
                cache.storeFrameworkId(FRAMEWORK_ID2);
                cache.consistencyCheckForTests();
                cache.clearFrameworkId();
                cache.consistencyCheckForTests();
            }
        });
    }

    @Test
    public void testStoreFrameworkIdFailureThenSuccess() {
        doThrow(new StateStoreException(Reason.UNKNOWN, "hello")).when(mockStore).storeFrameworkId(FRAMEWORK_ID);
        try {
            mockedCache.storeFrameworkId(FRAMEWORK_ID);
            fail("expected exception");
        } catch (StateStoreException e) {
            // expected
        }
        assertFalse(mockedCache.fetchFrameworkId().isPresent());

        doNothing().when(mockStore).storeFrameworkId(FRAMEWORK_ID);
        mockedCache.storeFrameworkId(FRAMEWORK_ID);
        assertEquals(FRAMEWORK_ID, mockedCache.fetchFrameworkId().get());
    }

    @Test
    public void testPropertiesSingleThread() {
        cache.consistencyCheckForTests();
        assertTrue(cache.fetchPropertyKeys().isEmpty());
        cache.storeProperty(PROP_KEY, PROP_VAL);
        cache.consistencyCheckForTests();
        assertEquals(1, cache.fetchPropertyKeys().size());
        assertArrayEquals(PROP_VAL, cache.fetchProperty(PROP_KEY));
        cache.storeProperty(PROP_KEY2, PROP_VAL2);
        cache.consistencyCheckForTests();
        assertEquals(2, cache.fetchPropertyKeys().size());
        assertArrayEquals(PROP_VAL, cache.fetchProperty(PROP_KEY));
        assertArrayEquals(PROP_VAL2, cache.fetchProperty(PROP_KEY2));
        cache.clearProperty(PROP_KEY);
        cache.consistencyCheckForTests();
        assertEquals(1, cache.fetchPropertyKeys().size());
        assertArrayEquals(PROP_VAL2, cache.fetchProperty(PROP_KEY2));
        cache.clearProperty(PROP_KEY2);
        cache.consistencyCheckForTests();
        assertTrue(cache.fetchPropertyKeys().isEmpty());
    }

    @Test
    public void testPropertiesMultiThread() throws InterruptedException {
        runThreads(new Runnable() {
            @Override
            public void run() {
                cache.consistencyCheckForTests();
                cache.storeProperty(PROP_KEY, PROP_VAL);
                cache.consistencyCheckForTests();
                cache.storeProperty(PROP_KEY2, PROP_VAL2);
                cache.consistencyCheckForTests();
                cache.clearProperty(PROP_KEY);
                cache.consistencyCheckForTests();
                cache.clearProperty(PROP_KEY2);
                cache.consistencyCheckForTests();
            }
        });
    }

    @Test
    public void testStorePropertyFailureThenSuccess() {
        doThrow(new StateStoreException(Reason.UNKNOWN, "hello")).when(mockStore).storeProperty(PROP_KEY2, PROP_VAL2);
        try {
            mockedCache.storeProperty(PROP_KEY2, PROP_VAL2);
            fail("expected exception");
        } catch (StateStoreException e) {
            // expected
        }
        try {
            mockedCache.fetchProperty(PROP_KEY2);
            fail("expected exception due to missing property");
        } catch (StateStoreException e) {
            // expected
        }

        doNothing().when(mockStore).storeProperty(PROP_KEY2, PROP_VAL2);
        mockedCache.storeProperty(PROP_KEY2, PROP_VAL2);
        assertArrayEquals(PROP_VAL2, mockedCache.fetchProperty(PROP_KEY2));
    }

    @Test
    public void testTaskInfoSingleThread() {
        cache.consistencyCheckForTests();
        assertTrue(cache.fetchTaskNames().isEmpty());
        cache.storeTasks(Arrays.asList(TASK));
        cache.consistencyCheckForTests();
        assertEquals(1, cache.fetchTaskNames().size());
        assertEquals(TASK, cache.fetchTask(TASK_NAME).get());
        cache.storeTasks(Arrays.asList(TASK2));
        cache.consistencyCheckForTests();
        assertEquals(2, cache.fetchTaskNames().size());
        assertEquals(TASK, cache.fetchTask(TASK_NAME).get());
        assertEquals(TASK2, cache.fetchTask(TASK_NAME2).get());
        cache.clearTask(TASK_NAME);
        cache.consistencyCheckForTests();
        assertEquals(1, cache.fetchTaskNames().size());
        assertEquals(TASK2, cache.fetchTask(TASK_NAME2).get());
        cache.clearTask(TASK_NAME2);
        cache.consistencyCheckForTests();
        assertTrue(cache.fetchTaskNames().isEmpty());
        cache.storeTasks(Arrays.asList(TASK, TASK2));
        cache.consistencyCheckForTests();
        assertEquals(2, cache.fetchTaskNames().size());
        assertEquals(TASK, cache.fetchTask(TASK_NAME).get());
        assertEquals(TASK2, cache.fetchTask(TASK_NAME2).get());
        cache.clearTask(TASK_NAME);
        cache.clearTask(TASK_NAME2);
        cache.consistencyCheckForTests();
        assertTrue(cache.fetchTaskNames().isEmpty());
    }

    @Test
    public void testTaskInfoMultiThread() throws InterruptedException {
        runThreads(new Runnable() {
            @Override
            public void run() {
                cache.consistencyCheckForTests();
                cache.storeTasks(Arrays.asList(TASK));
                cache.consistencyCheckForTests();
                cache.storeTasks(Arrays.asList(TASK2));
                cache.consistencyCheckForTests();
                cache.clearTask(TASK_NAME);
                cache.consistencyCheckForTests();
                cache.clearTask(TASK_NAME2);
                cache.consistencyCheckForTests();
                cache.storeTasks(Arrays.asList(TASK, TASK2));
                cache.consistencyCheckForTests();
                cache.clearTask(TASK_NAME);
                cache.clearTask(TASK_NAME2);
                cache.consistencyCheckForTests();
            }
        });
    }

    @Test
    public void testStoreTaskInfoFailureThenSuccess() {
        doThrow(new StateStoreException(Reason.UNKNOWN, "hello")).when(mockStore).storeTasks(Arrays.asList(TASK2));
        try {
            mockedCache.storeTasks(Arrays.asList(TASK2));
            fail("expected exception");
        } catch (StateStoreException e) {
            // expected
        }
        assertEquals(1, mockedCache.fetchTaskNames().size());
        assertEquals(1, mockedCache.fetchTasks().size());
        assertFalse(mockedCache.fetchTask(TASK_NAME2).isPresent());

        doNothing().when(mockStore).storeTasks(Arrays.asList(TASK2));
        mockedCache.storeTasks(Arrays.asList(TASK2));
        assertEquals(2, mockedCache.fetchTaskNames().size());
        assertEquals(2, mockedCache.fetchTasks().size());
        assertEquals(TASK2, mockedCache.fetchTask(TASK_NAME2).get());
    }

    @Test
    public void testStoreTaskInfoSameNameDifferentIDs() {
        // same name, different IDs:
        final TaskInfo taskA = TASK.toBuilder()
                .setName(TASK_NAME)
                .setTaskId(CommonTaskUtils.toTaskId(TASK_NAME))
                .build();
        final TaskInfo taskB = TASK.toBuilder()
                .setName(TASK_NAME)
                .setTaskId(CommonTaskUtils.toTaskId(TASK_NAME + "b"))
                .build();
        doNothing().when(mockStore).storeTasks(Arrays.asList(taskA, taskB));
        mockedCache.storeTasks(Arrays.asList(taskA, taskB));

        assertEquals(1, mockedCache.fetchTaskNames().size());
        assertEquals(1, mockedCache.fetchTasks().size());
        assertEquals(taskB, mockedCache.fetchTask(TASK_NAME).get());
    }

    @Test
    public void testTaskStatusSingleThread() {
        cache.consistencyCheckForTests();
        cache.storeTasks(Arrays.asList(TASK, TASK2));
        cache.consistencyCheckForTests();
        assertTrue(cache.fetchStatuses().isEmpty());
        cache.storeStatus(STATUS);
        cache.consistencyCheckForTests();
        assertEquals(1, cache.fetchStatuses().size());
        assertEquals(STATUS, cache.fetchStatus(TASK_NAME).get());
        cache.storeStatus(STATUS2);
        cache.consistencyCheckForTests();
        assertEquals(2, cache.fetchStatuses().size());
        assertEquals(STATUS, cache.fetchStatus(TASK_NAME).get());
        assertEquals(STATUS2, cache.fetchStatus(TASK_NAME2).get());
        cache.clearTask(TASK_NAME);
        cache.consistencyCheckForTests();
        assertEquals(1, cache.fetchStatuses().size());
        assertEquals(STATUS2, cache.fetchStatus(TASK_NAME2).get());
        cache.clearTask(TASK_NAME2);
        cache.consistencyCheckForTests();
        assertTrue(cache.fetchStatuses().isEmpty());
        cache.storeTasks(Arrays.asList(TASK, TASK2));
        cache.consistencyCheckForTests();
        cache.storeStatus(STATUS);
        cache.consistencyCheckForTests();
        cache.storeStatus(STATUS2);
        cache.consistencyCheckForTests();
        assertEquals(2, cache.fetchStatuses().size());
        assertEquals(STATUS, cache.fetchStatus(TASK_NAME).get());
        assertEquals(STATUS2, cache.fetchStatus(TASK_NAME2).get());
        cache.clearTask(TASK_NAME);
        cache.clearTask(TASK_NAME2);
        cache.consistencyCheckForTests();
        assertTrue(cache.fetchStatuses().isEmpty());
    }

    @Test
    public void testTaskStatusMultiThread() throws InterruptedException {
        // store tasks up-front so that status updates don't throw errors:
        cache.consistencyCheckForTests();
        cache.storeTasks(Arrays.asList(TASK, TASK2));
        cache.consistencyCheckForTests();
        runThreads(new Runnable() {
            @Override
            public void run() {
                // can't wipe tasks since that'll lead to unpredictable errors due to storing
                // taskstatus while taskinfo is missing
                cache.consistencyCheckForTests();
                cache.storeStatus(STATUS);
                cache.consistencyCheckForTests();
                cache.storeStatus(STATUS2);
                cache.consistencyCheckForTests();
                cache.storeTasks(Arrays.asList(TASK, TASK2));
                cache.consistencyCheckForTests();
                cache.storeStatus(STATUS);
                cache.consistencyCheckForTests();
                cache.storeStatus(STATUS2);
                cache.consistencyCheckForTests();
            }
        });
    }

    @Test
    public void testStoreTaskStatusFailureThenSuccess() throws Exception {
        doThrow(new StateStoreException(Reason.UNKNOWN, "hello")).when(mockStore).storeStatus(STATUS2);
        try {
            mockedCache.storeStatus(STATUS2);
            fail("expected exception");
        } catch (StateStoreException e) {
            // expected
        }
        assertFalse(mockedCache.fetchStatus(TASK_NAME2).isPresent());

        // fail to store data due to the lack of task->status mapping (in practice the underlying
        // storage should throw here):
        doNothing().when(mockStore).storeStatus(STATUS2);
        try {
            mockedCache.storeStatus(STATUS2);
            fail("expected exception");
        } catch (StateStoreException e) {
            // expected
        }
        assertFalse(mockedCache.fetchStatus(TASK_NAME2).isPresent());
        assertEquals(1, mockedCache.fetchStatuses().size());

        // with the TaskInfo provided first, it should now succeed:
        doNothing().when(mockStore).storeTasks(Arrays.asList(TASK2));
        mockedCache.storeTasks(Arrays.asList(TASK2));
        assertFalse(mockedCache.fetchStatus(TASK_NAME2).isPresent());
        mockedCache.storeStatus(STATUS2);
        assertEquals(STATUS2, mockedCache.fetchStatus(TASK_NAME2).get());
        assertEquals(2, mockedCache.fetchStatuses().size());
    }

    private static class TestStateStoreCache extends StateStoreCache {

        TestStateStoreCache(StateStore store) throws StateStoreException {
            super(store);
        }

        /**
         * A consistency check which may be called by tests to check for the following types of errors:
         * - Internal consistency errors (eg between nameToId/idToTask/idToStatus)
         * - External consistency errors between local state and underlying StateStore
         *
         * This function gets a state dump from the underlying StateStore and is therefore not expected
         * to be performant.
         *
         * @throws IllegalStateException in the event of any consistency failure
         */
        public void consistencyCheckForTests() {
            RLOCK.lock();
            try {
                // Phase 1: check internal consistency

                // If an name=>status entry exists, a matching name=>task entry must also exist.
                for (Map.Entry<String, TaskStatus> entry : nameToStatus.entrySet()) {
                    if (!nameToTask.containsKey(entry.getKey())) {
                        throw new IllegalStateException(String.format(
                                "nameToTask is missing nameToStatus entry: %s", entry));
                    }
                }

                // Phase 2: check consistency with StateStore

                // Local framework ID should match stored framework ID
                Optional<FrameworkID> storeFrameworkId = store.fetchFrameworkId();
                if (!storeFrameworkId.equals(frameworkId)) {
                    throw new IllegalStateException(String.format(
                            "Cache has frameworkId[%s] while storage has frameworkId[%s]",
                            frameworkId, storeFrameworkId));
                }
                // Local task names should match stored task names
                Set<String> storeNames = new HashSet<>(store.fetchTaskNames());
                if (!storeNames.equals(nameToTask.keySet())) {
                    throw new IllegalStateException(String.format(
                            "Cache has taskNames[%s] while storage has taskNames[%s]",
                            nameToTask.keySet(), storeNames));
                }
                // Local TaskInfos should match stored TaskInfos
                Map<String, TaskInfo> storeTasks = new HashMap<>();
                for (String taskName : storeNames) {
                    TaskInfo task = store.fetchTask(taskName).get();
                    storeTasks.put(task.getName(), task);
                }
                if (!storeTasks.equals(nameToTask)) {
                    throw new IllegalStateException(String.format(
                            "Cache has taskInfos[%s] while storage has taskInfos[%s]",
                            nameToTask, storeTasks));
                }
                // Local TaskStatuses should match stored TaskStatuses
                Map<String, TaskStatus> storeStatuses = new HashMap<>();
                for (String taskName : storeNames) {
                    Optional<TaskStatus> status = store.fetchStatus(taskName);
                    if (status.isPresent()) {
                        storeStatuses.put(taskName, status.get());
                    }
                }
                if (!storeStatuses.equals(nameToStatus)) {
                    throw new IllegalStateException(String.format(
                            "Cache has taskStatuses[%s] while storage has taskStatuses[%s]",
                            nameToStatus, storeStatuses));
                }
                // Local Properties should match stored Properties
                Map<String, byte[]> storeProperties = new HashMap<>();
                for (String propertyKey : store.fetchPropertyKeys()) {
                    storeProperties.put(propertyKey, store.fetchProperty(propertyKey));
                }
                if (!storeProperties.keySet().equals(properties.keySet())) {
                    throw new IllegalStateException(String.format(
                            "Cache has properties[%s] while storage has properties[%s]",
                            properties, storeProperties));
                }
                // manual deep comparison for the byte arrays:
                for (Map.Entry<String, byte[]> propEntry : properties.entrySet()) {
                    byte[] storeVal = storeProperties.get(propEntry.getKey());
                    if (!Arrays.equals(propEntry.getValue(), storeVal)) {
                        throw new IllegalStateException(String.format(
                                "Cache property value[%s=%s] doesn't match storage property value[%s=%s]",
                                propEntry.getKey(), propEntry.getValue(),
                                propEntry.getKey(), new String(storeVal, Charset.defaultCharset())));
                    }
                }
            } catch (Throwable e) {
                StringBuilder stateDump = new StringBuilder();
                stateDump.append("Consistency validation failure: ");
                stateDump.append(e.getMessage());
                stateDump.append("\nState dump:\n");
                stateDump.append("- frameworkId: ");
                stateDump.append(frameworkId);
                stateDump.append("\n- nameToTask: ");
                stateDump.append(nameToTask);
                stateDump.append("\n- nameToStatus: ");
                stateDump.append(nameToStatus);
                stateDump.append("\n- properties: ");
                stateDump.append(properties);
                stateDump.append('\n');
                throw new IllegalStateException(stateDump.toString(), e);
            } finally {
                RLOCK.unlock();
            }
        }
    }

    private static void runThreads(Runnable r) throws InterruptedException {
        final Object lock = new Object();
        final List<Throwable> errors = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Thread t = new Thread(r);
            t.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    synchronized (lock) {
                        errors.add(e);
                    }
                }
            });
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) {
            t.join();
        }
        assertTrue(errors.toString(), errors.isEmpty());
    }
}
