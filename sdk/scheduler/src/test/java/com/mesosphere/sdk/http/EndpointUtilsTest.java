package com.mesosphere.sdk.http;

import com.mesosphere.sdk.http.EndpointUtils.VipInfo;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.Before;

/**
 * Tests for {@link EndpointUtils}.
 */
public class EndpointUtilsTest {

    @Mock SchedulerConfig mockSchedulerConfig;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockSchedulerConfig.getApiServerPort()).thenReturn(1234);
        when(mockSchedulerConfig.getServiceTLD()).thenReturn("some.tld");
    }

    @Test
    public void testToEndpoint() {
        assertEquals("foo:5", EndpointUtils.toEndpoint("foo", 5));
    }

    @Test
    public void testToAutoIpEndpoint() {
        assertEquals("task.svc.autoip.dcos.thisdcos.directory:5", EndpointUtils.toAutoIpEndpoint("svc", "task", 5, SchedulerConfigTestUtils.getTestSchedulerConfig()));
        assertEquals("task.pathtosvc.autoip.dcos.thisdcos.directory:5", EndpointUtils.toAutoIpEndpoint("/path/to/svc", "task", 5, SchedulerConfigTestUtils.getTestSchedulerConfig()));
        assertEquals("task.pathtosvc.autoip.dcos.thisdcos.directory:5", EndpointUtils.toAutoIpEndpoint("path/to/svc", "task", 5, SchedulerConfigTestUtils.getTestSchedulerConfig()));
        assertEquals("task.pathtosvc-with-dots.autoip.dcos.thisdcos.directory:5", EndpointUtils.toAutoIpEndpoint("path/to/svc.with.dots", "task", 5, SchedulerConfigTestUtils.getTestSchedulerConfig()));
    }

    @Test
    public void testToAutoIpEndpointCustomTLD() {
        SchedulerConfig mockSchedulerConfig = SchedulerConfigTestUtils.getTestSchedulerConfig();
        when(mockSchedulerConfig.getServiceTLD()).thenReturn("what.a.fun.test.tld");

        assertEquals("task.svc.what.a.fun.test.tld:5", EndpointUtils.toAutoIpEndpoint("svc", "task", 5, mockSchedulerConfig));
        assertEquals("task.pathtosvc.what.a.fun.test.tld:5", EndpointUtils.toAutoIpEndpoint("/path/to/svc", "task", 5, mockSchedulerConfig));
        assertEquals("task.pathtosvc.what.a.fun.test.tld:5", EndpointUtils.toAutoIpEndpoint("path/to/svc", "task", 5, mockSchedulerConfig));
        assertEquals("task.pathtosvc-with-dots.what.a.fun.test.tld:5", EndpointUtils.toAutoIpEndpoint("path/to/svc.with.dots", "task", 5, mockSchedulerConfig));
    }

    @Test
    public void testToVipEndpoint() {
        assertEquals("vip.svc.l4lb.thisdcos.directory:5", EndpointUtils.toVipEndpoint("svc", new VipInfo("vip", 5)));
        assertEquals("vip.pathtosvc.l4lb.thisdcos.directory:5", EndpointUtils.toVipEndpoint("/path/to/svc", new VipInfo("vip", 5)));
        assertEquals("vip.pathtosvc.l4lb.thisdcos.directory:5", EndpointUtils.toVipEndpoint("path/to/svc", new VipInfo("vip", 5)));
        assertEquals("vip.pathtosvc.with.dots.l4lb.thisdcos.directory:5", EndpointUtils.toVipEndpoint("path/to/svc.with.dots", new VipInfo("vip", 5)));

        assertEquals("vip.svc.l4lb.thisdcos.directory:5", EndpointUtils.toVipEndpoint("svc", new VipInfo("/vip", 5)));
        assertEquals("vip.pathtosvc.l4lb.thisdcos.directory:5", EndpointUtils.toVipEndpoint("/path/to/svc", new VipInfo("/vip", 5)));
        assertEquals("vip.pathtosvc.l4lb.thisdcos.directory:5", EndpointUtils.toVipEndpoint("path/to/svc", new VipInfo("/vip", 5)));
        assertEquals("vip.pathtosvc.with.dots.l4lb.thisdcos.directory:5", EndpointUtils.toVipEndpoint("path/to/svc.with.dots", new VipInfo("/vip", 5)));
    }

    @Test
    public void testToSchedulerAutoIpHostname() {
        assertEquals("svc.marathon.some.tld", EndpointUtils.toSchedulerAutoIpHostname("svc", mockSchedulerConfig));
        assertEquals("svc-to-path.marathon.some.tld", EndpointUtils.toSchedulerAutoIpHostname("/path/to/svc", mockSchedulerConfig));
        assertEquals("svc-to-path.marathon.some.tld", EndpointUtils.toSchedulerAutoIpHostname("path/to/svc", mockSchedulerConfig));
        assertEquals("svc-with-dots-to-path.marathon.some.tld", EndpointUtils.toSchedulerAutoIpHostname("path/to/svc.with.dots", mockSchedulerConfig));
    }

    @Test
    public void testToSchedulerAutoIpEndpoint() {
        assertEquals("svc.marathon.some.tld:1234", EndpointUtils.toSchedulerAutoIpEndpoint("svc", mockSchedulerConfig));
        assertEquals("svc-to-path.marathon.some.tld:1234", EndpointUtils.toSchedulerAutoIpEndpoint("/path/to/svc", mockSchedulerConfig));
        assertEquals("svc-to-path.marathon.some.tld:1234", EndpointUtils.toSchedulerAutoIpEndpoint("path/to/svc", mockSchedulerConfig));
        assertEquals("svc-with-dots-to-path.marathon.some.tld:1234", EndpointUtils.toSchedulerAutoIpEndpoint("path/to/svc.with.dots", mockSchedulerConfig));
    }
}
