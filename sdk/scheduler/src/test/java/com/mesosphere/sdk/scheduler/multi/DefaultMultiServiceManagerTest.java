package com.mesosphere.sdk.scheduler.multi;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.ServiceSpec;

public class DefaultMultiServiceManagerTest {

    @Mock private ServiceSpec mockServiceSpec1;
    @Mock private ServiceSpec mockServiceSpec2;
    @Mock private DefaultScheduler mockClient1;
    @Mock private DefaultScheduler mockClient2;
    @Mock private ServiceSpec mockUninstallServiceSpec;
    @Mock private UninstallScheduler mockUninstallClient;

    private DefaultMultiServiceManager multiServiceManager;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockClient1.getServiceSpec()).thenReturn(mockServiceSpec1);
        when(mockClient2.getServiceSpec()).thenReturn(mockServiceSpec2);
        when(mockServiceSpec1.getName()).thenReturn("1");
        when(mockServiceSpec2.getName()).thenReturn("2");
        multiServiceManager = new DefaultMultiServiceManager();
    }

    @Test
    public void putClientsDuplicate() {
        multiServiceManager.putService(mockClient1);
        multiServiceManager.putService(mockClient2);
        Assert.assertEquals(new HashSet<>(Arrays.asList("1", "2")), multiServiceManager.getServiceNames());
        try {
            multiServiceManager.putService(mockClient2);
            Assert.fail("Expected exception: duplicate key");
        } catch (IllegalArgumentException e) {
            // expected
        }
        multiServiceManager.removeServices(Collections.singletonList("2"));
        multiServiceManager.putService(mockClient2);
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
            // expected
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
        try {
            multiServiceManager.putService(mockClient2);
            Assert.fail("Expected exception: duplicate key");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void uninstallRequestedClient() {
        when(mockClient1.toUninstallScheduler()).thenReturn(mockUninstallClient);
        when(mockUninstallClient.getServiceSpec()).thenReturn(mockUninstallServiceSpec);
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
