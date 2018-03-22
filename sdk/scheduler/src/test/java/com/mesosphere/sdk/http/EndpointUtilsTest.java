package com.mesosphere.sdk.http;

import com.mesosphere.sdk.http.EndpointUtils.VipInfo;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link EndpointUtils}.
 */
public class EndpointUtilsTest {

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
        Mockito.when(mockSchedulerConfig.getServiceTLD()).thenReturn("what.a.fun.test.tld");

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
    public void testToSchedulerApiVipHostname() {
        assertEquals("api.svc.marathon.l4lb.thisdcos.directory", EndpointUtils.toSchedulerApiVipHostname("svc"));
        assertEquals("api.pathtosvc.marathon.l4lb.thisdcos.directory", EndpointUtils.toSchedulerApiVipHostname("/path/to/svc"));
        assertEquals("api.pathtosvc.marathon.l4lb.thisdcos.directory", EndpointUtils.toSchedulerApiVipHostname("path/to/svc"));
        assertEquals("api.pathtosvc.with.dots.marathon.l4lb.thisdcos.directory", EndpointUtils.toSchedulerApiVipHostname("path/to/svc.with.dots"));
    }
}
