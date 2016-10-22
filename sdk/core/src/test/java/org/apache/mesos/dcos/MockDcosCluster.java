package org.apache.mesos.dcos;

import io.netty.handler.codec.http.HttpHeaders;
import org.mockserver.integration.ClientAndProxy;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.springframework.http.MediaType;

import java.net.URI;
import java.net.URISyntaxException;

import static org.mockserver.integration.ClientAndProxy.startClientAndProxy;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * This class encapsulates a DC/OS Cluster which is mocked by a HTTP Server and Proxy.
 */

public class MockDcosCluster {
    public static final String TEST_BOOTSTRAP_ID = "test-bootstrap-id";
    public static final String TEST_DCOS_IMAGE_COMMIT = "test-dcos-image-commit";

    private static final int mockServerPort = 1080;
    private static final int mockProxyPort = 1090;
    private ClientAndServer mockServer;
    private ClientAndProxy mockProxy;
    private URI testDcosUri;
    private DcosCluster dcosCluster;
    private String testVersion;

    public static MockDcosCluster create(String testVersion) throws URISyntaxException {
        return new MockDcosCluster(testVersion);
    }

    private MockDcosCluster(String testVersion) throws URISyntaxException {
        this.testVersion = testVersion;
        testDcosUri = new URI("http://127.0.0.1:" + getServerPort());
        mockServer = startClientAndServer(getServerPort());
        mockProxy = startClientAndProxy(getProxyPort());
        dcosCluster = new DcosCluster(testDcosUri);
        initializePaths();
    }

    public URI getUri() {
        return testDcosUri;
    }

    public int getServerPort() {
        return mockServerPort;
    }

    public int getProxyPort() {
        return mockProxyPort;
    }

    public DcosCluster getDcosCluster() {
        return dcosCluster;
    }

    public void stop() {
        mockProxy.stop();
        mockServer.stop();
    }

    private void initializePaths() {
        mockServer
                .when(
                        request()
                                .withPath("/dcos-metadata/dcos-version.json")
                )
                .respond(
                        response()
                                .withHeaders(
                                        new Header(HttpHeaders.Names.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                )
                                .withBody("" +
                                        "    {" + System.getProperty("line.separator") +
                                        "        \"bootstrap-id\": \"" + TEST_BOOTSTRAP_ID + "\"," + System.getProperty("line.separator") +
                                        "        \"dcos-image-commit\": \"" + TEST_DCOS_IMAGE_COMMIT + "\"," + System.getProperty("line.separator") +
                                        "        \"version\": \"" + testVersion + "\"," + System.getProperty("line.separator") +
                                        "    }")
                );
    }
}
