package com.mesosphere.sdk.curator;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CreateBuilder;
import org.apache.curator.framework.api.ExistsBuilder;
import org.apache.curator.framework.api.GetDataBuilder;
import org.apache.curator.framework.api.ProtectACLCreateModePathAndBytesable;
import org.apache.zookeeper.KeeperException;

/**
 * Tests for {@link CuratorUtils}.
 */
public class CuratorUtilsTest {
    @Mock private CuratorFramework mockClient;
    @Mock private GetDataBuilder mockGetDataBuilder;
    @Mock private CreateBuilder mockCreateBuilder;
    @Mock private ProtectACLCreateModePathAndBytesable<String> mockCreateParentsBuilder;
    @Mock private ExistsBuilder mockExistsBuilder;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetServiceRootPath() {
        assertEquals("/dcos-service-test", CuratorUtils.getServiceRootPath("/test"));
        assertEquals("/dcos-service-test", CuratorUtils.getServiceRootPath("test"));

        assertEquals("/dcos-service-path__to__myteam__test", CuratorUtils.getServiceRootPath("/path/to/myteam/test"));
        assertEquals("/dcos-service-path__to__myteam__test", CuratorUtils.getServiceRootPath("path/to/myteam/test"));

        assertEquals("/dcos-service-__test", CuratorUtils.getServiceRootPath("//test"));
        assertEquals("/dcos-service-path__to__myteam____test", CuratorUtils.getServiceRootPath("/path/to/myteam//test"));
    }

    @Test
    public void testInitServicePathNewService() throws Exception {
        String originalServiceName = "/folder/path/to/myservice";
        Persister persister = new CuratorPersister(originalServiceName, mockClient);

        Mockito.when(mockClient.getData()).thenReturn(mockGetDataBuilder);
        Mockito.when(mockGetDataBuilder.forPath(Mockito.anyString()))
                .thenThrow(new KeeperException.NoNodeException());

        Mockito.when(mockClient.create()).thenReturn(mockCreateBuilder);
        Mockito.when(mockCreateBuilder.creatingParentsIfNeeded()).thenReturn(mockCreateParentsBuilder);

        CuratorUtils.initServiceName(persister, originalServiceName);

        Mockito.verify(mockGetDataBuilder).forPath(
                Mockito.eq("/dcos-service-folder__path__to__myservice/servicename"));
        Mockito.verify(mockCreateParentsBuilder).forPath(
                Mockito.eq("/dcos-service-folder__path__to__myservice/servicename"),
                Mockito.eq(originalServiceName.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testInitServicePathExistingService() throws Exception {
        String originalServiceName = "/folder/path/to/myservice";
        Persister persister = new CuratorPersister(originalServiceName, mockClient);

        Mockito.when(mockClient.getData()).thenReturn(mockGetDataBuilder);
        Mockito.when(mockGetDataBuilder.forPath(Mockito.anyString()))
                .thenReturn(originalServiceName.getBytes(StandardCharsets.UTF_8));

        CuratorUtils.initServiceName(persister, originalServiceName);

        Mockito.verify(mockGetDataBuilder).forPath(
                Mockito.eq("/dcos-service-folder__path__to__myservice/servicename"));
    }

    @Test
    public void testInitServicePathExistingServiceMismatch() throws Exception {
        String originalServiceName = "/folder/path/to/myservice";
        Persister persister = new CuratorPersister(originalServiceName, mockClient);

        Mockito.when(mockClient.getData()).thenReturn(mockGetDataBuilder);
        Mockito.when(mockGetDataBuilder.forPath(Mockito.anyString()))
                .thenReturn("othervalue".getBytes(StandardCharsets.UTF_8));

        try {
            CuratorUtils.initServiceName(persister, originalServiceName);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Collision"));
        }

        Mockito.verify(mockGetDataBuilder).forPath(
                Mockito.eq("/dcos-service-folder__path__to__myservice/servicename"));
    }

    @Test
    public void testServiceNameCollision() {
        Persister persister = new MemPersister();
        CuratorUtils.initServiceName(persister, "/path/to/myservice");
        try {
            CuratorUtils.initServiceName(persister, "/path/to__myservice");
            fail("expected exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Collision"));
        }
    }
}
