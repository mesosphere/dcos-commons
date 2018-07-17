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
        when(mockSchedulerConfig.getAutoipTLD()).thenReturn("autoip.tld");
        when(mockSchedulerConfig.getVipTLD()).thenReturn("vip.tld");
        when(mockSchedulerConfig.getMarathonName()).thenReturn("test-marathon");
    }

    @Test
    public void testToEndpoint() {
        assertEquals("foo:5", EndpointUtils.toEndpoint("foo", 5));
    }

    @Test
    public void testToAutoIpEndpoint() {
        assertEquals("task.svc.autoip.tld:5",
                EndpointUtils.toAutoIpEndpoint("svc", "task", 5, SchedulerConfigTestUtils.getTestSchedulerConfig()));
        assertEquals("task.pathtosvc.autoip.tld:5",
                EndpointUtils.toAutoIpEndpoint("/path/to/svc", "task", 5, SchedulerConfigTestUtils.getTestSchedulerConfig()));
        assertEquals("task.pathtosvc.autoip.tld:5",
                EndpointUtils.toAutoIpEndpoint("path/to/svc", "task", 5, SchedulerConfigTestUtils.getTestSchedulerConfig()));
        assertEquals("task.pathtosvc-with-dots.autoip.tld:5",
                EndpointUtils.toAutoIpEndpoint("path/to/svc.with.dots", "task", 5, SchedulerConfigTestUtils.getTestSchedulerConfig()));
    }

    @Test
    public void testToAutoIpEndpointCustomTLD() {
        SchedulerConfig mockSchedulerConfig = SchedulerConfigTestUtils.getTestSchedulerConfig();
        when(mockSchedulerConfig.getAutoipTLD()).thenReturn("what.a.fun.test.tld");

        assertEquals("task.svc.what.a.fun.test.tld:5",
                EndpointUtils.toAutoIpEndpoint("svc", "task", 5, mockSchedulerConfig));
        assertEquals("task.pathtosvc.what.a.fun.test.tld:5",
                EndpointUtils.toAutoIpEndpoint("/path/to/svc", "task", 5, mockSchedulerConfig));
        assertEquals("task.pathtosvc.what.a.fun.test.tld:5",
                EndpointUtils.toAutoIpEndpoint("path/to/svc", "task", 5, mockSchedulerConfig));
        assertEquals("task.pathtosvc-with-dots.what.a.fun.test.tld:5",
                EndpointUtils.toAutoIpEndpoint("path/to/svc.with.dots", "task", 5, mockSchedulerConfig));
    }

    @Test
    public void testToVipEndpoint() {
        assertEquals("vip.svc.vip.tld:5",
                EndpointUtils.toVipEndpoint("svc", mockSchedulerConfig, new VipInfo("vip", 5)));
        assertEquals("vip.pathtosvc.vip.tld:5",
                EndpointUtils.toVipEndpoint("/path/to/svc", mockSchedulerConfig, new VipInfo("vip", 5)));
        assertEquals("vip.pathtosvc.vip.tld:5",
                EndpointUtils.toVipEndpoint("path/to/svc", mockSchedulerConfig, new VipInfo("vip", 5)));
        assertEquals("vip.pathtosvc.with.dots.vip.tld:5",
                EndpointUtils.toVipEndpoint("path/to/svc.with.dots", mockSchedulerConfig, new VipInfo("vip", 5)));

        assertEquals("vip.svc.vip.tld:5",
                EndpointUtils.toVipEndpoint("svc", mockSchedulerConfig, new VipInfo("/vip", 5)));
        assertEquals("vip.pathtosvc.vip.tld:5",
                EndpointUtils.toVipEndpoint("/path/to/svc", mockSchedulerConfig, new VipInfo("/vip", 5)));
        assertEquals("vip.pathtosvc.vip.tld:5",
                EndpointUtils.toVipEndpoint("path/to/svc", mockSchedulerConfig, new VipInfo("/vip", 5)));
        assertEquals("vip.pathtosvc.with.dots.vip.tld:5",
                EndpointUtils.toVipEndpoint("path/to/svc.with.dots", mockSchedulerConfig, new VipInfo("/vip", 5)));
    }

    @Test
    public void testToSchedulerAutoIpHostname() {
        assertEquals("svc.test-marathon.autoip.tld",
                EndpointUtils.toSchedulerAutoIpHostname("svc", mockSchedulerConfig));
        assertEquals("svc-to-path.test-marathon.autoip.tld",
                EndpointUtils.toSchedulerAutoIpHostname("/path/to/svc", mockSchedulerConfig));
        assertEquals("svc-to-path.test-marathon.autoip.tld",
                EndpointUtils.toSchedulerAutoIpHostname("path/to/svc", mockSchedulerConfig));
        assertEquals("svc-with-dots-to-path.test-marathon.autoip.tld",
                EndpointUtils.toSchedulerAutoIpHostname("path/to/svc.with.dots", mockSchedulerConfig));
    }

    @Test
    public void testToSchedulerAutoIpEndpoint() {
        assertEquals("svc.test-marathon.autoip.tld:1234",
                EndpointUtils.toSchedulerAutoIpEndpoint("svc", mockSchedulerConfig));
        assertEquals("svc-to-path.test-marathon.autoip.tld:1234",
                EndpointUtils.toSchedulerAutoIpEndpoint("/path/to/svc", mockSchedulerConfig));
        assertEquals("svc-to-path.test-marathon.autoip.tld:1234",
                EndpointUtils.toSchedulerAutoIpEndpoint("path/to/svc", mockSchedulerConfig));
        assertEquals("svc-with-dots-to-path.test-marathon.autoip.tld:1234",
                EndpointUtils.toSchedulerAutoIpEndpoint("path/to/svc.with.dots", mockSchedulerConfig));
    }
}
