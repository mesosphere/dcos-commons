package org.apache.mesos.api;

import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.mesos.testutils.NetworkTestUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import java.io.IOException;
import java.util.Arrays;

/**
 * This class tests the JettyApiServer.
 */
public class JettyApiServerTest {
    private JettyApiServer jettyApiServer;
    private int port;
    private static final String TEST = "test";

    @Before
    public void beforeEach() throws IOException {
        MockitoAnnotations.initMocks(this);
        port = NetworkTestUtils.getRandomPort();
        jettyApiServer = new JettyApiServer(port, Arrays.asList(new TestPojo()));
    }

    @After
    public void afterEach() throws Exception {
        jettyApiServer.stop();
    }

    @Test
    public void testGetRequest() throws Exception {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    jettyApiServer.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        HttpClient client = HttpClientBuilder.create().build();
        HttpGet request = new HttpGet(String.format("http://0.0.0.0:%s/%s", port, TEST));
        HttpEntity entity = client.execute(request).getEntity();
        String responseString = EntityUtils.toString(entity, "UTF-8");

        Assert.assertEquals(TEST, responseString);
    }

    @Path("/" + TEST)
    public static class TestPojo {
        @GET
        public String getString() {
            return TEST;
        }
    }
}
