package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.specification.DefaultNetworkSpec;
import com.mesosphere.sdk.specification.NetworkSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import sun.nio.ch.Net;

import java.util.*;

import static org.mockito.Mockito.when;

/**
 * Created by Rand on 5/18/17.
 */
public class PodSpecsCannorChangeNetworkRegimeTest {

    @Mock
    private PodSpec mockPodSpecDcosOverlay;
    @Mock
    private PodSpec mockPodSpecBridgeOverlay;
    @Mock
    private PodSpec mockPodSpecHostNetwork;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);

        DefaultNetworkSpec overlayNetworkSpec, bridgeOverlayNetworkSpec;
        overlayNetworkSpec = new DefaultNetworkSpec(DcosConstants.DEFAULT_OVERLAY_NETWORK,
                Collections.emptyMap(), Collections.emptyMap());
        bridgeOverlayNetworkSpec = new DefaultNetworkSpec("mesos-bridge",
                Collections.emptyMap(), Collections.emptyMap());

        List<NetworkSpec> overlayNetworkSpecs = new ArrayList<>(Collections.singletonList(overlayNetworkSpec));
        List<NetworkSpec> bridgeNetworkSpecs = new ArrayList<>(Collections.singletonList(bridgeOverlayNetworkSpec));
        List<NetworkSpec> onHostNetwork = new ArrayList<>();

        when(mockPodSpecDcosOverlay.getNetworks()).thenReturn(overlayNetworkSpecs);
        when(mockPodSpecBridgeOverlay.getNetworks()).thenReturn(bridgeNetworkSpecs);
        when(mockPodSpecHostNetwork.getNetworks()).thenReturn(onHostNetwork);
    }

    @Test
    public void testStaysOnOverlay() throws InvalidRequirementException {

    }

}
