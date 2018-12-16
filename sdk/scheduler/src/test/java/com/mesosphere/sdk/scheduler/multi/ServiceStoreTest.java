package com.mesosphere.sdk.scheduler.multi;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ServiceStore}
 */
public class ServiceStoreTest {

    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final byte[] FOO_CONTEXT = "foo-data".getBytes(CHARSET);
    private static final byte[] BAR_CONTEXT = "bar-data".getBytes(CHARSET);

    @Mock private ServiceFactory mockServiceFactory;
    @Mock private AbstractScheduler mockSchedulerFoo;
    @Mock private AbstractScheduler mockSchedulerBar;
    @Mock private ServiceSpec mockSpecFoo;
    @Mock private ServiceSpec mockSpecBar;

    private Persister persister;
    private ServiceStore store;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mockServiceFactory.buildService(FOO_CONTEXT)).thenReturn(mockSchedulerFoo);
        when(mockServiceFactory.buildService(BAR_CONTEXT)).thenReturn(mockSchedulerBar);
        when(mockSchedulerFoo.getServiceSpec()).thenReturn(mockSpecFoo);
        when(mockSchedulerBar.getServiceSpec()).thenReturn(mockSpecBar);
        when(mockSpecFoo.getName()).thenReturn("foo");
        when(mockSpecBar.getName()).thenReturn("bar");

        persister = MemPersister.newBuilder().build();
        store = new ServiceStore(persister, mockServiceFactory);
    }

    @Test
    public void testPutGetUninstall() throws Exception {
        Assert.assertFalse(store.get("foo").isPresent());
        Assert.assertEquals(mockSchedulerFoo, store.put(FOO_CONTEXT));
        verify(mockServiceFactory).buildService(FOO_CONTEXT);
        Assert.assertEquals(FOO_CONTEXT, store.get("foo").get());
        Assert.assertEquals(1, store.recover().size());

        Assert.assertFalse(store.get("bar").isPresent());
        Assert.assertEquals(mockSchedulerBar, store.put(BAR_CONTEXT));
        verify(mockServiceFactory).buildService(BAR_CONTEXT);

        Assert.assertEquals(FOO_CONTEXT, store.get("foo").get());
        Assert.assertEquals(BAR_CONTEXT, store.get("bar").get());
        Assert.assertEquals(2, store.recover().size());

        store.getUninstallCallback().uninstalled("foo");
        Assert.assertFalse(store.get("foo").isPresent());
        Assert.assertEquals(1, store.recover().size());

        Assert.assertEquals(BAR_CONTEXT, store.get("bar").get());
        store.getUninstallCallback().uninstalled("bar");
        Assert.assertFalse(store.get("foo").isPresent());
        Assert.assertFalse(store.get("bar").isPresent());
        Assert.assertEquals(0, store.recover().size());
    }

    @Test
    public void testPutFactoryFails() throws Exception {
        when(mockServiceFactory.buildService(BAR_CONTEXT)).thenThrow(new Exception("BANG"));
        try {
            store.put(BAR_CONTEXT);
        } catch (Exception e) {
            Assert.assertEquals("BANG", e.getMessage());
        }
        // Data not stored due to factory failure:
        Assert.assertFalse(store.get("bar").isPresent());
    }

    @Test
    public void testSlashedName() throws Exception {
        Assert.assertFalse(store.get("/path/to/foo").isPresent());
        when(mockServiceFactory.buildService(FOO_CONTEXT)).thenReturn(mockSchedulerFoo);
        when(mockSpecFoo.getName()).thenReturn("/path/to/foo");
        Assert.assertEquals(mockSchedulerFoo, store.put(FOO_CONTEXT));
        verify(mockServiceFactory).buildService(FOO_CONTEXT);
        Assert.assertEquals(FOO_CONTEXT, store.get("/path/to/foo").get());
        Assert.assertArrayEquals(FOO_CONTEXT, persister.get("/ServiceList/path__to__foo/Context"));

        store.getUninstallCallback().uninstalled("/path/to/foo");
        Assert.assertFalse(store.get("/path/to/foo").isPresent());
    }

    @Test
    public void testRecover() throws Exception {
        // Store data against one instance:
        Assert.assertEquals(mockSchedulerFoo, store.put(FOO_CONTEXT));
        Assert.assertEquals(mockSchedulerBar, store.put(BAR_CONTEXT));
        verify(mockServiceFactory).buildService(FOO_CONTEXT);
        verify(mockServiceFactory).buildService(BAR_CONTEXT);
        Assert.assertEquals(FOO_CONTEXT, store.get("foo").get());
        Assert.assertEquals(BAR_CONTEXT, store.get("bar").get());

        // Create new instance against the same persister (verify no state within store itself):
        store = new ServiceStore(persister, mockServiceFactory);
        Assert.assertEquals(FOO_CONTEXT, store.get("foo").get());
        Assert.assertEquals(BAR_CONTEXT, store.get("bar").get());

        Collection<AbstractScheduler> recovered = store.recover();
        Assert.assertEquals(2, recovered.size());
        Assert.assertTrue(recovered.contains(mockSchedulerFoo));
        Assert.assertTrue(recovered.contains(mockSchedulerBar));

        // Factory should have been invoked twice: Once for the initial put(), and then again for the recover():
        verify(mockServiceFactory, times(2)).buildService(FOO_CONTEXT);
        verify(mockServiceFactory, times(2)).buildService(BAR_CONTEXT);
    }

    @Test
    public void testRecoverFactoryFails() throws Exception {
        // Store data against one instance:
        Assert.assertEquals(mockSchedulerFoo, store.put(FOO_CONTEXT));
        Assert.assertEquals(mockSchedulerBar, store.put(BAR_CONTEXT));
        Assert.assertEquals(FOO_CONTEXT, store.get("foo").get());
        Assert.assertEquals(BAR_CONTEXT, store.get("bar").get());

        // Create new instance against the same persister (verify no state within store itself):
        store = new ServiceStore(persister, mockServiceFactory);
        Assert.assertEquals(FOO_CONTEXT, store.get("foo").get());
        Assert.assertEquals(BAR_CONTEXT, store.get("bar").get());

        // If 'foo' fails to recover, it should just be skipped in the output:
        when(mockServiceFactory.buildService(FOO_CONTEXT)).thenThrow(new Exception("BANG"));
        Collection<AbstractScheduler> recovered = store.recover();
        Assert.assertEquals(1, recovered.size());
        Assert.assertFalse(recovered.contains(mockSchedulerFoo));
        Assert.assertTrue(recovered.contains(mockSchedulerBar));
    }
}
