package com.mesosphere.sdk.dcos.clients;

import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Response;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.dcos.DcosHttpExecutor;
import com.mesosphere.sdk.dcos.DcosVersion;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.mockito.Mockito.*;

/**
 * Tests for the {@link DcosVersionClient} class.
 */
public class DcosVersionClientTest {

    private static final String TEST_VERSION = "1.9-dev";
    public static final String TEST_BOOTSTRAP_ID = "test-bootstrap-id";
    public static final String TEST_DCOS_IMAGE_COMMIT = "test-dcos-image-commit";
    private static final String RESPONSE_TEMPLATE =
            "{ 'version': '%s', " +
            "'dcos-image-commit': '" + TEST_DCOS_IMAGE_COMMIT + "', " +
            "'bootstrap-id': '" + TEST_BOOTSTRAP_ID + "' }";

    @Mock private DcosHttpExecutor mockHttpExecutor;
    @Mock private Response mockResponse;
    @Mock private Content mockResponseContent;

    private DcosVersionClient client;

    @Before
    public void beforeAll() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mockHttpExecutor.execute(DcosVersionClient.DCOS_VERSION_REQUEST)).thenReturn(mockResponse);
        when(mockResponse.returnContent()).thenReturn(mockResponseContent);

        client = new DcosVersionClient(mockHttpExecutor);
    }

    @Test
    public void testGetVersion() throws IOException, URISyntaxException {
        when(mockResponseContent.toString()).thenReturn(String.format(RESPONSE_TEMPLATE, TEST_VERSION));
        DcosVersion dcosVersion = client.getDcosVersion();
        Assert.assertEquals(TEST_VERSION, dcosVersion.getVersion());
        Assert.assertEquals(1, dcosVersion.getElements().getFirstElement());
        Assert.assertEquals(9, dcosVersion.getElements().getSecondElement());
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionInt() throws IOException, URISyntaxException {
        when(mockResponseContent.toString()).thenReturn(String.format(RESPONSE_TEMPLATE, "5"));
        DcosVersion dcosVersion = client.getDcosVersion();
        Assert.assertEquals("5", dcosVersion.getVersion());
        Assert.assertEquals(5, dcosVersion.getElements().getFirstElement());
        dcosVersion.getElements().getSecondElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionIntDot() throws IOException, URISyntaxException {
        when(mockResponseContent.toString()).thenReturn(String.format(RESPONSE_TEMPLATE, "0."));
        DcosVersion dcosVersion = client.getDcosVersion();
        Assert.assertEquals("0.", dcosVersion.getVersion());
        Assert.assertEquals(0, dcosVersion.getElements().getFirstElement());
        dcosVersion.getElements().getSecondElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionDot() throws IOException, URISyntaxException {
        when(mockResponseContent.toString()).thenReturn(String.format(RESPONSE_TEMPLATE, "."));
        DcosVersion dcosVersion = client.getDcosVersion();
        Assert.assertEquals(".", dcosVersion.getVersion());
        dcosVersion.getElements().getFirstElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionString() throws IOException, URISyntaxException {
        when(mockResponseContent.toString()).thenReturn(String.format(RESPONSE_TEMPLATE, "0.hello"));
        DcosVersion dcosVersion = client.getDcosVersion();
        Assert.assertEquals("0.hello", dcosVersion.getVersion());
        Assert.assertEquals(0, dcosVersion.getElements().getFirstElement());
        dcosVersion.getElements().getSecondElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionSuffix() throws IOException, URISyntaxException {
        when(mockResponseContent.toString()).thenReturn(String.format(RESPONSE_TEMPLATE, "0.5-hey"));
        DcosVersion dcosVersion = client.getDcosVersion();
        Assert.assertEquals("0.5-hey", dcosVersion.getVersion());
        Assert.assertEquals(0, dcosVersion.getElements().getFirstElement());
        dcosVersion.getElements().getSecondElement();
    }
}
