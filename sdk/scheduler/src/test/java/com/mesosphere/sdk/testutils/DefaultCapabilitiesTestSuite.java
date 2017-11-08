package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.ResourceRefinementCapabilityContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class provides default a context with the default set of capabilities.
 */
public abstract class DefaultCapabilitiesTestSuite {
    private static ResourceRefinementCapabilityContext context;

    @BeforeClass
    public static final void beforeAllSuites() throws Exception {
        Capabilities capabilities = mock(Capabilities.class);
        when(capabilities.supportsGpuResource()).thenReturn(true);
        when(capabilities.supportsCNINetworking()).thenReturn(true);
        when(capabilities.supportsNamedVips()).thenReturn(true);
        when(capabilities.supportsRLimits()).thenReturn(true);
        when(capabilities.supportsPreReservedResources()).thenReturn(true);
        when(capabilities.supportsFileBasedSecrets()).thenReturn(true);
        when(capabilities.supportsEnvBasedSecretsProtobuf()).thenReturn(true);
        when(capabilities.supportsEnvBasedSecretsDirectiveLabel()).thenReturn(true);
        context = new ResourceRefinementCapabilityContext(capabilities);

    }

    @AfterClass
    public static final void afterAll() {
        context.reset();
    }
}
