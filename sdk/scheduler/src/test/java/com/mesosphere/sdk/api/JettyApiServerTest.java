package com.mesosphere.sdk.api;

import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Arrays;

/**
 * This class tests the JettyApiServer.
 */
public class JettyApiServerTest {
    private JettyApiServer jettyApiServer;
    private static final String TEST = "test";

    @Before
    public void beforeEach() throws IOException {
        // using a busy-wait and retry pattern to reach the sync point where
        // server and client connect. This approach was chosen b/c it took a
        // minimal amount of effort to use a generally applicable pattern to
        // resolve local build/test failures, but a more elegant solution may
        // be to use LifeCycle.Listener.
        final int jettyStartRetries = 10;
        final int jettyStartRetryDelay = 1000;
        MockitoAnnotations.initMocks(this);

        new Thread(new Runnable() {
            @Override
            public void run() {
                for(int i = 0; i < jettyStartRetries; i++) {
                    try {
                        jettyApiServer = new JettyApiServer(0, Arrays.asList(new TestPojo()));
                        jettyApiServer.start();
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    sleep(jettyStartRetryDelay);
                }
            }
        }).start();

        for(int i = 0; i < jettyStartRetries; i++) {
            if (jettyApiServer != null && jettyApiServer.isStarted()) {
                break;
            }
            sleep(jettyStartRetryDelay);
        }
    }

    @After
    public void afterEach() throws Exception {
        jettyApiServer.stop();
    }

    @Test
    public void testGetRequest() throws Exception {
        HttpClient client = HttpClientBuilder.create().build();
        HttpGet request = new HttpGet(String.format("http://127.0.0.1:%s/%s", getJettyApiServerPort(), TEST));
        HttpEntity entity = client.execute(request).getEntity();
        String responseString = EntityUtils.toString(entity, "UTF-8");
        Assert.assertEquals(TEST, responseString);
    }

    @Path("/")
    public static class TestPojo {
        @Path("/" + TEST)
        @GET
        public Response getFrameworkId() {
            return Response.ok(TEST, MediaType.APPLICATION_JSON).build();
        }
    }

    private void sleep(int ms) {
        // an interruptable sleep b/c if interrupted, i.e. by SIGINT, np,
        // continue to crash, don't worry about sleep
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) { }
    }

    private int getJettyApiServerPort() {
        if (jettyApiServer == null) {
            return -1;
        }
        if (!jettyApiServer.isStarted()) {
            return -2;
        }
        return jettyApiServer.getLocalPort();
    }
}
