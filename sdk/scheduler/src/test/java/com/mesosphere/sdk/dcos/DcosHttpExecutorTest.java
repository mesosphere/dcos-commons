package com.mesosphere.sdk.dcos;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DcosHttpExecutor}.
 */
public class DcosHttpExecutorTest {

    @Mock private HttpClientBuilder mockClientBuilder;
    @Mock private CloseableHttpClient mockClient;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockClientBuilder.build()).thenReturn(mockClient);
    }

    @Test
    public void testInitializationVerified() {
        DcosCertInstaller.setInitialized(false);
        try {
            new DcosHttpExecutor(mockClientBuilder);
            fail("Expected exception");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("before certificates were installed"));
        }
        DcosCertInstaller.setInitialized(true);
        new DcosHttpExecutor(mockClientBuilder);
    }
}
