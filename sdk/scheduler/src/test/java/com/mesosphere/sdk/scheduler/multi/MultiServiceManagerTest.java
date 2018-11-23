package com.mesosphere.sdk.scheduler.multi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link MultiServiceManager}
 */
public class MultiServiceManagerTest {

    @Mock private ServiceSpec mockServiceSpec1;
    @Mock private ServiceSpec mockServiceSpec2;
    @Mock private DefaultScheduler mockClient1;
    @Mock private DefaultScheduler mockClient2;
    @Mock private UninstallScheduler mockUninstallClient1;
    @Mock private UninstallScheduler mockUninstallClient2;
    @Mock private ServiceSpec mockUninstallServiceSpec;

    private MultiServiceManager multiServiceManager;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockClient1.getServiceSpec()).thenReturn(mockServiceSpec1);
        when(mockClient2.getServiceSpec()).thenReturn(mockServiceSpec2);
        when(mockServiceSpec1.getName()).thenReturn("1");
        when(mockServiceSpec2.getName()).thenReturn("2");
        multiServiceManager = new MultiServiceManager(SchedulerConfigTestUtils.getTestSchedulerConfig());
    }

    @Test
    public void noDeadlockOnRegisterCalls() {
        final Collection<String> loopbackCalls = new ArrayList<>();

        Answer<Void> answer1 = new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                multiServiceManager.getService("1");
                loopbackCalls.add("1");
                return null;
            }
        };
        Answer<Void> answer2 = new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                multiServiceManager.getServiceNames();
                loopbackCalls.add("2");
                return null;
            }
        };

        // Simulate Mesos client behavior of calling back into us after we do something:
        Mockito.doAnswer(answer1).when(mockClient1).registered(anyBoolean());
        Mockito.doAnswer(answer1).when(mockUninstallClient1).registered(anyBoolean());
        Mockito.doAnswer(answer2).when(mockClient2).registered(anyBoolean());
        Mockito.doAnswer(answer2).when(mockUninstallClient2).registered(anyBoolean());

        // Now attempt each of the operations which should result in calling registered() on underlying clients, and
        // verify that we don't hit a deadlock.

        multiServiceManager.registered(false);

        multiServiceManager.putService(mockClient1);
        verify(mockClient1).registered(false);
        Assert.assertEquals(Collections.singletonList("1"), loopbackCalls);

        multiServiceManager.putService(mockClient2);
        verify(mockClient2).registered(false);
        Assert.assertEquals(Arrays.asList("1", "2"), loopbackCalls);

        multiServiceManager.registered(true);
        verify(mockClient1).registered(true);
        verify(mockClient2).registered(true);
        Assert.assertEquals(Arrays.asList("1", "2", "1", "2"), loopbackCalls);

        when(mockClient1.toUninstallScheduler()).thenReturn(mockUninstallClient1);
        when(mockClient2.toUninstallScheduler()).thenReturn(mockUninstallClient2);
        multiServiceManager.uninstallServices(Arrays.asList("1", "2"));
        verify(mockUninstallClient1).registered(false);
        verify(mockUninstallClient2).registered(false);
        Assert.assertEquals(Arrays.asList("1", "2", "1", "2", "1", "2"), loopbackCalls);
    }

    @Test
    public void putClientsDuplicate() {
        multiServiceManager.putService(mockClient1);
        Assert.assertEquals(new HashSet<>(Arrays.asList("1")), multiServiceManager.getServiceNames());
        multiServiceManager.putService(mockClient2);
        Assert.assertEquals(new HashSet<>(Arrays.asList("1", "2")), multiServiceManager.getServiceNames());

        // Reconfiguration:
        multiServiceManager.putService(mockClient2);
        Assert.assertEquals(new HashSet<>(Arrays.asList("1", "2")), multiServiceManager.getServiceNames());

        multiServiceManager.removeServices(Collections.singletonList("2"));
        Assert.assertEquals(new HashSet<>(Arrays.asList("1")), multiServiceManager.getServiceNames());

        multiServiceManager.putService(mockClient2);
        Assert.assertEquals(new HashSet<>(Arrays.asList("1", "2")), multiServiceManager.getServiceNames());
    }

    @Test
    public void putClientsDuplicateFoldered() {
        when(mockServiceSpec1.getName()).thenReturn("/path/to/1");
        when(mockServiceSpec2.getName()).thenReturn("/path.to/1");
        multiServiceManager.putService(mockClient1);
        Assert.assertEquals(new HashSet<>(Arrays.asList("/path/to/1")), multiServiceManager.getServiceNames());
        try {
            multiServiceManager.putService(mockClient2);
            Assert.fail("Expected exception: duplicate key");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals(
                    "Service named '/path.to/1' conflicts with existing service '/path/to/1': matching sanitized name 'path.to.1'",
                    e.getMessage());
        }
        multiServiceManager.removeServices(Collections.singletonList("/path/to/1"));
        multiServiceManager.putService(mockClient2);
    }

    @Test
    public void clientsFromStatus() {
        when(mockServiceSpec1.getName()).thenReturn("/path/to/1");
        when(mockServiceSpec2.getName()).thenReturn("2");
        multiServiceManager.putService(mockClient1);
        multiServiceManager.putService(mockClient2);
        Assert.assertEquals(new HashSet<>(Arrays.asList("/path/to/1", "2")), multiServiceManager.getServiceNames());

        Assert.assertEquals(mockClient1, multiServiceManager.getMatchingService(buildStatus("/path/to/1")).get());
        Assert.assertEquals(mockClient1, multiServiceManager.getMatchingService(buildStatus("path.to.1")).get());
        Assert.assertFalse(multiServiceManager.getMatchingService(buildStatus("/path/to/2")).isPresent());
        Assert.assertFalse(multiServiceManager.getMatchingService(buildStatus("path.to.2")).isPresent());
        Assert.assertEquals(mockClient2, multiServiceManager.getMatchingService(buildStatus("2")).get());

        multiServiceManager.removeServices(Arrays.asList("/path/to/1", "2"));

        Assert.assertFalse(multiServiceManager.getMatchingService(buildStatus("/path/to/1")).isPresent());
        Assert.assertFalse(multiServiceManager.getMatchingService(buildStatus("path.to.1")).isPresent());
        Assert.assertFalse(multiServiceManager.getMatchingService(buildStatus("2")).isPresent());
    }

    @Test
    public void putClientsRegistration() {
        multiServiceManager.putService(mockClient1);
        multiServiceManager.registered(false);
        verify(mockClient1).registered(false);
        multiServiceManager.putService(mockClient2);
        // Should have been called automatically due to already being registered:
        verify(mockClient2).registered(false);
        // Reconfiguration should re-invoke registered:
        multiServiceManager.putService(mockClient2);
        verify(mockClient2, times(2)).registered(false);
    }

    @Test
    public void uninstallRequestedClient() {
        when(mockClient1.toUninstallScheduler()).thenReturn(mockUninstallClient1);
        when(mockUninstallClient1.getServiceSpec()).thenReturn(mockUninstallServiceSpec);
        when(mockUninstallServiceSpec.getName()).thenReturn("1");
        multiServiceManager.putService(mockClient1);
        // 2 and second 1 are ignored:
        multiServiceManager.uninstallServices(Arrays.asList("1", "1", "2"));
        Assert.assertEquals(1, multiServiceManager.getServiceNames().size());
        verify(mockClient1).toUninstallScheduler();

        multiServiceManager.removeServices(Collections.singleton("1"));
        Assert.assertEquals(0, multiServiceManager.getServiceNames().size());
    }

    private static Protos.TaskStatus buildStatus(String clientName) {
        return Protos.TaskStatus.newBuilder()
                .setTaskId(CommonIdUtils.toTaskId(clientName, "foo"))
                .setState(TaskState.TASK_FINISHED)
                .build();
    }
}
