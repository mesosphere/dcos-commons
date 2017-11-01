package com.mesosphere.sdk.offer.taskdata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Label;
import org.junit.Test;
import org.mockito.Mockito;

import com.mesosphere.sdk.api.EndpointUtils;
import com.mesosphere.sdk.api.EndpointUtils.VipInfo;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.testutils.TestConstants;

/**
 * Tests for {@link AuxLabelAccess}.
 */
public class AuxLabelAccessTest {

    @Test
    public void testCreateVipLabel() {
        Protos.Port.Builder portBuilder = newPortBuilder();
        AuxLabelAccess.setVIPLabels(portBuilder, newVIPSpec("vip", 5));
        Collection<Protos.Label> labels = portBuilder.getLabels().getLabelsList();
        assertEquals(1, labels.size());
        Label label = labels.iterator().next();
        assertTrue(label.getKey().startsWith("VIP_"));
        assertEquals("vip:5", label.getValue());

        Collection<EndpointUtils.VipInfo> vips =
                AuxLabelAccess.getVIPsFromLabels(TestConstants.TASK_NAME, portBuilder.build());
        assertEquals(1, vips.size());
        EndpointUtils.VipInfo vip = vips.iterator().next();
        assertEquals("vip", vip.getVipName());
        assertEquals(5, vip.getVipPort());
    }

    @Test
    public void testCreateVipLabelOnOverlay() {
        Protos.Port.Builder portBuilder = newPortBuilder();
        AuxLabelAccess.setVIPLabels(portBuilder, newVIPSpec("vip", 5, "dcos"));
        Collection<Protos.Label> labels = portBuilder.getLabels().getLabelsList();
        assertEquals(2, labels.size());
        assertEquals(1, labels.stream()
                .filter(label -> label.getKey().startsWith("VIP_") && label.getValue().equals("vip:5"))
                .collect(Collectors.toList()).size());
        assertEquals(1, labels.stream()
                .filter(label -> label.getKey().equals(LabelConstants.VIP_OVERLAY_FLAG_KEY) &&
                        label.getValue().equals(LabelConstants.VIP_OVERLAY_FLAG_VALUE))
                .collect(Collectors.toList()).size());

        Collection<EndpointUtils.VipInfo> vips =
                AuxLabelAccess.getVIPsFromLabels(TestConstants.TASK_NAME, portBuilder.build());
        assertEquals(1, vips.size());
        EndpointUtils.VipInfo vip = vips.iterator().next();
        assertEquals("vip", vip.getVipName());
        assertEquals(5, vip.getVipPort());
    }

    @Test
    public void testParseVipLabel() {
        assertEquals(0, AuxLabelAccess.getVIPsFromLabels(TestConstants.TASK_NAME, withLabel("", "")).size());
        assertEquals(0, AuxLabelAccess.getVIPsFromLabels(TestConstants.TASK_NAME, withLabel("asdf", "ara")).size());
        assertEquals(0, AuxLabelAccess.getVIPsFromLabels(TestConstants.TASK_NAME, withLabel("VIP_0000", "ara")).size());
        assertEquals(0, AuxLabelAccess.getVIPsFromLabels(TestConstants.TASK_NAME, withLabel("VIP_0000", "ara:rar")).size());

        VipInfo info = AuxLabelAccess.getVIPsFromLabels(TestConstants.TASK_NAME, withLabel("VIP_0000", "myvip:321")).iterator().next();
        assertEquals("myvip", info.getVipName());
        assertEquals(321, info.getVipPort());
    }

    private static NamedVIPSpec newVIPSpec(String name, int port, String... networkNames) {
        NamedVIPSpec mockVIPSpec = Mockito.mock(NamedVIPSpec.class);
        Mockito.when(mockVIPSpec.getVipName()).thenReturn(name);
        Mockito.when(mockVIPSpec.getVipPort()).thenReturn(port);
        Mockito.when(mockVIPSpec.getNetworkNames()).thenReturn(Arrays.asList(networkNames));
        return mockVIPSpec;
    }

    private static Protos.Port withLabel(String key, String value) {
        Protos.Port.Builder portBuilder = newPortBuilder();
        portBuilder.getLabelsBuilder().addLabelsBuilder()
                .setKey(key)
                .setValue(value);
        return portBuilder.build();
    }

    private static Protos.Port.Builder newPortBuilder() {
        return Protos.Port.newBuilder().setNumber(999);
    }
}
