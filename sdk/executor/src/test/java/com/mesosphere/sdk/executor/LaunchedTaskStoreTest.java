package com.mesosphere.sdk.executor;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

public class LaunchedTaskStoreTest {

    private static final String MESSAGE = "Shutting down!!!";

    @Mock Runnable mockExitCallback;
    @Mock LaunchedTask mockLaunchedTask;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        doThrow(new IllegalStateException(MESSAGE)).when(mockExitCallback).run();
    }

    @Test
    public void testDoneTaskAdded() {
        ExecutorService executor = Executors.newCachedThreadPool();
        LaunchedTaskStore store = new LaunchedTaskStore(mockExitCallback, 10);
        Future<?> exited = executor.submit(store.getMonitor());
        Assert.assertFalse(exited.isDone());

        when(mockLaunchedTask.isDone()).thenReturn(true);
        store.put(Protos.TaskID.newBuilder().setValue("foo").build(), mockLaunchedTask);

        // check that self-destruct shutdown call was invoked, and that monitor exited afterwards:
        try {
            exited.get();
            Assert.fail("expected exception from shutdown invocation");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof ExecutionException);
            Assert.assertEquals(MESSAGE, e.getCause().getMessage());
        }
    }

    @Test
    public void testRunningThenDoneTaskAdded() {
        ExecutorService executor = Executors.newCachedThreadPool();
        LaunchedTaskStore store = new LaunchedTaskStore(mockExitCallback, 10);
        Future<?> exited = executor.submit(store.getMonitor());
        Assert.assertFalse(exited.isDone());

        // Done only after several calls:
        when(mockLaunchedTask.isDone()).thenAnswer(new Answer<Boolean>() {
            private int count = 0;

            @Override
            public Boolean answer(InvocationOnMock invocation) {
                return count++ == 5;
            }
        });
        store.put(Protos.TaskID.newBuilder().setValue("foo").build(), mockLaunchedTask);

        // check that self-destruct shutdown call was invoked, and that monitor exited afterwards:
        try {
            exited.get();
            Assert.fail("expected exception from shutdown invocation");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof ExecutionException);
            Assert.assertEquals(MESSAGE, e.getCause().getMessage());
        }
    }

    @Test
    public void testAlwaysRunningTaskAdded() {
        ExecutorService executor = Executors.newCachedThreadPool();
        LaunchedTaskStore store = new LaunchedTaskStore(mockExitCallback, 10);
        Future<?> exited = executor.submit(store.getMonitor());
        Assert.assertFalse(exited.isDone());

        when(mockLaunchedTask.isDone()).thenReturn(false);
        store.put(Protos.TaskID.newBuilder().setValue("foo").build(), mockLaunchedTask);

        // check that self-destruct shutdown call was invoked, and that monitor exited afterwards:
        try {
            exited.get(500, TimeUnit.MILLISECONDS);
            Assert.fail("expected timeout exception when waiting for check to exit");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof TimeoutException);
        }
    }
}
