package com.mesosphere.sdk.dcos;

import org.junit.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.scheduler.SchedulerConfig;

import java.io.IOException;

import static org.mockito.Mockito.*;

/**
 * Tests for the {@link Capabilities} class
 */
public class CapabilitiesTest {
    @Mock SchedulerConfig mockSchedulerConfig;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(false);
    }

    @Test
    public void test_090() throws IOException {
        Capabilities capabilities = testCapabilities("0.9.0");
        Assert.assertFalse(capabilities.supportsNamedVips());
        Assert.assertFalse(capabilities.supportsRLimits());

        // Region awareness
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(true);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(false);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
    }

    @Test
    public void test_170() throws IOException {
        Capabilities capabilities = testCapabilities("1.7.0");
        Assert.assertFalse(capabilities.supportsNamedVips());
        Assert.assertFalse(capabilities.supportsRLimits());

        // Region awareness
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(true);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(false);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
    }

    @Test
    public void test_17dev() throws IOException {
        Capabilities capabilities = testCapabilities("1.7-dev");
        Assert.assertFalse(capabilities.supportsNamedVips());
        Assert.assertFalse(capabilities.supportsRLimits());

        // Region awareness
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(true);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(false);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
    }

    @Test
    public void test_180() throws IOException {
        Capabilities capabilities = testCapabilities("1.8.0");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertFalse(capabilities.supportsRLimits());
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsDirectiveLabel());

        // Region awareness
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(true);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(false);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
    }

    @Test
    public void test_18dev() throws IOException {
        Capabilities capabilities = testCapabilities("1.8-dev");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertFalse(capabilities.supportsRLimits());
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsDirectiveLabel());

        // Region awareness
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(true);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(false);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
    }

    @Test
    public void test_190() throws IOException {
        Capabilities capabilities = testCapabilities("1.9.0");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertTrue(capabilities.supportsGpuResource());
        Assert.assertTrue(capabilities.supportsRLimits());
        //Secrets
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsDirectiveLabel());
        Assert.assertFalse(capabilities.supportsEnvBasedSecretsProtobuf());
        Assert.assertFalse(capabilities.supportsFileBasedSecrets());

        // Region awareness
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(true);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(false);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
    }

    @Test
    public void test_19dev() throws IOException {
        Capabilities capabilities = testCapabilities("1.9-dev");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertTrue(capabilities.supportsGpuResource());
        Assert.assertTrue(capabilities.supportsRLimits());
        //Secrets
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsDirectiveLabel());
        Assert.assertFalse(capabilities.supportsEnvBasedSecretsProtobuf());
        Assert.assertFalse(capabilities.supportsFileBasedSecrets());

        // Region awareness
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(true);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(false);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
    }

    @Test
    public void test_1100() throws IOException {
        Capabilities capabilities = testCapabilities("1.10.0");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertTrue(capabilities.supportsGpuResource());
        Assert.assertTrue(capabilities.supportsRLimits());
        // Secrets
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsDirectiveLabel());
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsProtobuf());
        Assert.assertTrue(capabilities.supportsFileBasedSecrets());

        Assert.assertFalse(capabilities.supportsV1APIByDefault());

        // Region awareness
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(true);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(false);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
    }

    @Test
    public void test_110dev() throws IOException {
        Capabilities capabilities = testCapabilities("1.10-dev");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertTrue(capabilities.supportsGpuResource());
        Assert.assertTrue(capabilities.supportsRLimits());
        // Secrets
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsDirectiveLabel());
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsProtobuf());
        Assert.assertTrue(capabilities.supportsFileBasedSecrets());

        Assert.assertFalse(capabilities.supportsV1APIByDefault());

        // Region awareness
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(true);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(false);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
    }

    @Test
    public void test_1110() throws IOException {
        Capabilities capabilities = testCapabilities("1.11.0");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertTrue(capabilities.supportsGpuResource());
        Assert.assertTrue(capabilities.supportsRLimits());
        // Secrets
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsDirectiveLabel());
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsProtobuf());
        Assert.assertTrue(capabilities.supportsFileBasedSecrets());

        Assert.assertTrue(capabilities.supportsV1APIByDefault());

        // Region awareness
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(true);
        Assert.assertTrue(capabilities.supportsRegionAwareness(mockSchedulerConfig));
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(false);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
    }

    @Test
    public void test_111dev() throws IOException {
        Capabilities capabilities = testCapabilities("1.11-dev");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertTrue(capabilities.supportsGpuResource());
        Assert.assertTrue(capabilities.supportsRLimits());
        // Secrets
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsDirectiveLabel());
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsProtobuf());
        Assert.assertTrue(capabilities.supportsFileBasedSecrets());

        Assert.assertTrue(capabilities.supportsV1APIByDefault());

        // Region awareness
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(true);
        Assert.assertTrue(capabilities.supportsRegionAwareness(mockSchedulerConfig));
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(false);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
    }

    @Test
    public void test_200() throws IOException {
        Capabilities capabilities = testCapabilities("2.0.0");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertTrue(capabilities.supportsGpuResource());
        Assert.assertTrue(capabilities.supportsRLimits());
        // Secrets
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsDirectiveLabel());
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsProtobuf());
        Assert.assertTrue(capabilities.supportsFileBasedSecrets());

        // Region awareness
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(true);
        Assert.assertTrue(capabilities.supportsRegionAwareness(mockSchedulerConfig));
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(false);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
    }

    @Test
    public void test_20dev() throws IOException {
        Capabilities capabilities = testCapabilities("2.0-dev");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertTrue(capabilities.supportsGpuResource());
        Assert.assertTrue(capabilities.supportsRLimits());
        // Secrets
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsDirectiveLabel());
        Assert.assertTrue(capabilities.supportsEnvBasedSecretsProtobuf());
        Assert.assertTrue(capabilities.supportsFileBasedSecrets());

        // Region awareness
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(true);
        Assert.assertTrue(capabilities.supportsRegionAwareness(mockSchedulerConfig));
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(false);
        Assert.assertFalse(capabilities.supportsRegionAwareness(mockSchedulerConfig));
    }

    private Capabilities testCapabilities(String version) throws IOException {
        return new Capabilities(new DcosVersion(version));
    }
}
