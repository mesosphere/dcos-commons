package com.mesosphere.sdk.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.mesosphere.sdk.dcos.DcosConstants;
import org.apache.mesos.Protos.Label;
import org.junit.Test;

import com.mesosphere.sdk.api.EndpointUtils.VipInfo;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

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
        assertEquals("task.svc.autoip.dcos.thisdcos.directory:5", EndpointUtils.toAutoIpEndpoint("svc", "task", 5));
        assertEquals("task.pathtosvc.autoip.dcos.thisdcos.directory:5", EndpointUtils.toAutoIpEndpoint("/path/to/svc", "task", 5));
        assertEquals("task.pathtosvc.autoip.dcos.thisdcos.directory:5", EndpointUtils.toAutoIpEndpoint("path/to/svc", "task", 5));
        assertEquals("task.pathtosvc-with-dots.autoip.dcos.thisdcos.directory:5", EndpointUtils.toAutoIpEndpoint("path/to/svc.with.dots", "task", 5));
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

    @Test
    public void testCreateVipLabel() {
        Collection<Label> labels = EndpointUtils.createVipLabels("vip", 5, Collections.emptyList());
        assertEquals(1, labels.size());
        Label label = labels.iterator().next();
        assertTrue(label.getKey().startsWith("VIP_"));
        assertEquals("vip:5", label.getValue());
    }

    @Test
    public void testCreateVipLabelOnOverlay() {
        Collection<Label> labels = EndpointUtils.createVipLabels("vip", 5,
                Arrays.asList(DcosConstants.DEFAULT_OVERLAY_NETWORK));
        assertEquals(2, labels.size());
        assertEquals(1, labels.stream()
                .filter(label -> label.getKey().startsWith("VIP_") && label.getValue().equals("vip:5"))
                .collect(Collectors.toList()).size());
        assertEquals(1, labels.stream()
                .filter(label -> label.getKey().equals(DcosConstants.VIP_OVERLAY_FLAG_KEY) &&
                        label.getValue().equals(DcosConstants.VIP_OVERLAY_FLAG_VALUE))
                .collect(Collectors.toList()).size());
    }

    @Test
    public void testParseVipLabel() {
        assertFalse(EndpointUtils.parseVipLabel("", Label.getDefaultInstance()).isPresent());
        assertFalse(EndpointUtils.parseVipLabel("", Label.newBuilder().setKey("asdf").setValue("ara").build()).isPresent());
        assertFalse(EndpointUtils.parseVipLabel("", Label.newBuilder().setKey("VIP_0000").setValue("ara").build()).isPresent());
        assertFalse(EndpointUtils.parseVipLabel("", Label.newBuilder().setKey("VIP_0000").setValue("ara:rar").build()).isPresent());
        VipInfo info = EndpointUtils.parseVipLabel("", Label.newBuilder().setKey("VIP_0000").setValue("vip:5").build()).get();
        assertEquals("vip", info.getVipName());
        assertEquals(5, info.getVipPort());
    }
}
