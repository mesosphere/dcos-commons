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
    private static Capabilities capabilities;

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    public DefaultCapabilitiesTestSuite() {
        capabilities = mock(Capabilities.class);
        when(capabilities.supportsGpuResource()).thenReturn(true);
        when(capabilities.supportsCNINetworking()).thenReturn(true);
        when(capabilities.supportsNamedVips()).thenReturn(true);
        when(capabilities.supportsRLimits()).thenReturn(true);
        when(capabilities.supportsPreReservedResources()).thenReturn(true);
        when(capabilities.supportsFileBasedSecrets()).thenReturn(true);
        when(capabilities.supportsEnvBasedSecretsProtobuf()).thenReturn(true);
        when(capabilities.supportsEnvBasedSecretsDirectiveLabel()).thenReturn(true);
    }

    @BeforeClass
    public static void beforeAll() throws Exception {
        context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
    }

    @AfterClass
    public static void afterAll() {
        context.reset();
    }
}
