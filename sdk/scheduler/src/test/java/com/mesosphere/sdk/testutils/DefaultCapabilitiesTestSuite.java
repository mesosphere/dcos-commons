package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.dcos.Capabilities;
import org.junit.BeforeClass;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class provides default a context with the default set of capabilities.
 */
public abstract class DefaultCapabilitiesTestSuite {
    @BeforeClass
    public static final void beforeAllSuites() throws Exception {
        Capabilities capabilities = mock(Capabilities.class);
        when(capabilities.supportsDefaultExecutor()).thenReturn(true);
        when(capabilities.supportsGpuResource()).thenReturn(true);
        when(capabilities.supportsCNINetworking()).thenReturn(true);
        when(capabilities.supportsNamedVips()).thenReturn(true);
        when(capabilities.supportsRLimits()).thenReturn(true);
        when(capabilities.supportsPreReservedResources()).thenReturn(true);
        when(capabilities.supportsFileBasedSecrets()).thenReturn(true);
        when(capabilities.supportsEnvBasedSecretsProtobuf()).thenReturn(true);
        when(capabilities.supportsEnvBasedSecretsDirectiveLabel()).thenReturn(true);
        Capabilities.overrideCapabilities(capabilities);
    }
}
