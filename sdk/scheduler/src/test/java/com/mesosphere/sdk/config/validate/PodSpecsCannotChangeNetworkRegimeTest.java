package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.mockito.Mockito.when;

/**
 * Tests for {@link PodSpecsCannotChangeNetworkRegime}.
 */
public class PodSpecsCannotChangeNetworkRegimeTest {
    private static final ConfigValidator<ServiceSpec> VALIDATOR = new PodSpecsCannotChangeNetworkRegime();

    @Mock
    private PodSpec overlaypod1;
    @Mock
    private PodSpec overlaypod2;

    @Mock
    private PodSpec bridgepod1;
    @Mock
    private PodSpec bridgepod2;

    @Mock
    private PodSpec hostpod1;
    @Mock
    private PodSpec hostpod2;

    private List<PodSpec> overlayPods = new ArrayList<>();
    private List<PodSpec> hostPods = new ArrayList<>();
    private List<PodSpec> bridgePods = new ArrayList<>();

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);

        DefaultNetworkSpec overlayNetworkSpec, bridgeOverlayNetworkSpec;
        overlayNetworkSpec = new DefaultNetworkSpec("dcos", Collections.emptyMap(), Collections.emptyMap());
        bridgeOverlayNetworkSpec = new DefaultNetworkSpec("mesos-bridge", Collections.emptyMap(), Collections.emptyMap());

        List<NetworkSpec> overlayNetworkSpecs = new ArrayList<>(Collections.singletonList(overlayNetworkSpec));
        List<NetworkSpec> bridgeNetworkSpecs = new ArrayList<>(Collections.singletonList(bridgeOverlayNetworkSpec));
        List<NetworkSpec> onHostNetwork = new ArrayList<>();


        when(overlaypod1.getType()).thenReturn(TestConstants.POD_TYPE + "-1");
        when(overlaypod2.getType()).thenReturn(TestConstants.POD_TYPE + "-2");
        when(overlaypod1.getNetworks()).thenReturn(overlayNetworkSpecs);
        when(overlaypod2.getNetworks()).thenReturn(overlayNetworkSpecs);
        overlayPods.addAll(Arrays.asList(overlaypod1, overlaypod2));

        when(bridgepod1.getType()).thenReturn(TestConstants.POD_TYPE + "-1");
        when(bridgepod2.getType()).thenReturn(TestConstants.POD_TYPE + "-2");
        when(bridgepod1.getNetworks()).thenReturn(bridgeNetworkSpecs);
        when(bridgepod2.getNetworks()).thenReturn(bridgeNetworkSpecs);
        bridgePods.addAll(Arrays.asList(bridgepod1, bridgepod2));

        when(hostpod1.getType()).thenReturn(TestConstants.POD_TYPE + "-1");
        when(hostpod2.getType()).thenReturn(TestConstants.POD_TYPE + "-2");
        when(hostpod1.getNetworks()).thenReturn(onHostNetwork);
        when(hostpod2.getNetworks()).thenReturn(onHostNetwork);
        hostPods.addAll(Arrays.asList(hostpod1, hostpod2));


    }

    private void testConfigTransition(List<PodSpec> oldPodSpecs, List<PodSpec> newPodSpecs,
                                      int expectedHomoTransitionErrors, int expectedHeteroTransitionErrors)
            throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(oldPodSpecs)
                .build();
        ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(newPodSpecs)
                .build();

        Assert.assertEquals(expectedHeteroTransitionErrors, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(expectedHeteroTransitionErrors, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(expectedHomoTransitionErrors, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(expectedHomoTransitionErrors, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());
    }

    @Test
    public void testStaysOnOverlay() throws InvalidRequirementException {
        testConfigTransition(overlayPods, overlayPods, 0, 0);
    }

    @Test
    public void testStaysOnHost() throws InvalidRequirementException {
        testConfigTransition(hostPods, hostPods, 0, 0);
    }

    @Test
    public void testStaysOnBridge() throws InvalidRequirementException {
        testConfigTransition(bridgePods, bridgePods, 0, 0);
    }

    @Test
    public void testIllegalSwitchOverlayToHost() throws InvalidRequirementException {
        testConfigTransition(hostPods, overlayPods, 0, 2);
    }

    @Test
    public void illegalSwitchOverlayToBridge() throws InvalidRequirementException {
        testConfigTransition(bridgePods, overlayPods, 0, 2);
    }

    @Test
    public void testSwitchFromBridgeToHost() throws InvalidRequirementException {
        testConfigTransition(bridgePods, hostPods, 0, 0);
    }
}
