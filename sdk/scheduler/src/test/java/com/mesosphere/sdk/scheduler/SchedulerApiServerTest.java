package com.mesosphere.sdk.scheduler;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.ContentResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHttpRequest;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SchedulerApiServerTest {
    private static final int SHORT_TIMEOUT_MILLIS = 100;
    private static final int LONG_TIMEOUT_MILLIS = 30000;

    @BeforeClass
    public static void beforeAll() {
        org.eclipse.jetty.util.log.StdErrLog l = new org.eclipse.jetty.util.log.StdErrLog();
        l.setLevel(org.eclipse.jetty.util.log.StdErrLog.LEVEL_ALL);
        org.eclipse.jetty.util.log.Log.setLog(l);
    }

    @Test
    public void testApiServerEndpointManagement() throws Exception {
        SchedulerConfig mockSchedulerConfig = mock(SchedulerConfig.class);
        when(mockSchedulerConfig.getApiServerInitTimeout()).thenReturn(Duration.ofMillis(LONG_TIMEOUT_MILLIS));
        when(mockSchedulerConfig.getApiServerPort()).thenReturn(0);

        Listener listener = new Listener();
        SchedulerApiServer server = SchedulerApiServer.start(mockSchedulerConfig, listener);
        listener.waitForStarted();

        server.addResources("v1", Arrays.asList(new TestResourceOneTwo(), new TestResourceThree()));
        server.addResources("foo", Arrays.asList(new TestResourceFour()));
        server.addResources("", Arrays.asList(new TestResourceThree(), new TestResourceFour()));

        Map<String, String> expectedEndpoints = new HashMap<>();
        expectedEndpoints.put("/v1/metrics", "");
        expectedEndpoints.put("/v1/metrics/prometheus", "");
        expectedEndpoints.put("/v1/onetwo/endpoint1", "one");
        expectedEndpoints.put("/v1/onetwo/endpoint2", "two");
        expectedEndpoints.put("/v1/three/endpoint3", "three");
        expectedEndpoints.put("/foo/four/endpoint4", "four");
        expectedEndpoints.put("/three/endpoint3", "three");
        expectedEndpoints.put("/four/endpoint4", "four");

        checkEndpoints(server.getURI(), expectedEndpoints);

        server.removeResources("foo");
        expectedEndpoints.put("/foo/four/endpoint4", null);

        checkEndpoints(server.getURI(), expectedEndpoints);

        server.addResources("foo", Arrays.asList(new TestResourceFour()));
        expectedEndpoints.put("/foo/four/endpoint4", "four");

        checkEndpoints(server.getURI(), expectedEndpoints);

        server.removeResources("v1");
        expectedEndpoints.put("/v1/onetwo/endpoint1", null);
        expectedEndpoints.put("/v1/onetwo/endpoint2", null);
        expectedEndpoints.put("/v1/three/endpoint3", null);

        checkEndpoints(server.getURI(), expectedEndpoints);

        server.removeResources("");
        expectedEndpoints.put("/three/endpoint3", null);
        expectedEndpoints.put("/three/endpoint4", null);

        checkEndpoints(server.getURI(), expectedEndpoints);

        server.stop();
    }

    private static void checkEndpoints(URI server, Map<String, String> endpoints) throws Exception {
        HttpHost host = new HttpHost(server.getHost(), server.getPort());
        for (Map.Entry<String, String> entry : endpoints.entrySet()) {
            HttpResponse response = HttpClientBuilder.create().build()
                    .execute(host, new BasicHttpRequest("GET", entry.getKey()));
            if (entry.getValue() == null) {
                // Verify return value 404 only
                Assert.assertEquals(entry.getKey() + ": " + endpoints.toString(),
                        404, response.getStatusLine().getStatusCode());
            } else if (entry.getValue().isEmpty()) {
                // Verify return value 200 only
                Assert.assertEquals(entry.getKey() + ": " + endpoints.toString(),
                        200, response.getStatusLine().getStatusCode());
            } else {
                // Verify content and return value
                Assert.assertEquals(entry.getKey() + ": " + endpoints.toString(), entry.getValue(), new ContentResponseHandler()
                        .handleEntity(response.getEntity())
                        .asString());
                Assert.assertEquals(entry.getKey() + ": " + endpoints.toString(),
                        200, response.getStatusLine().getStatusCode());
            }
        }
    }

    private static class Listener implements Runnable {
        private final AtomicBoolean apiServerStarted = new AtomicBoolean(false);

        @Override
        public void run() {
            apiServerStarted.set(true);
        }

        private void waitForStarted() {
            int maxSleepCount = LONG_TIMEOUT_MILLIS / SHORT_TIMEOUT_MILLIS;
            for (int i = 0; i < maxSleepCount && !apiServerStarted.get(); ++i) {
                try {
                    Thread.sleep(SHORT_TIMEOUT_MILLIS);
                } catch (Exception e) {
                    // ignore and continue
                }
            }
            Assert.assertTrue(apiServerStarted.get());

        }
    }

    @Path("onetwo")
    private static class TestResourceOneTwo {

        @Path("/endpoint1")
        @GET
        public Response endpoint1() {
            return Response.ok().entity("one").build();
        }
        @Path("/endpoint2")
        @GET
        public Response endpoint2() {
            return Response.ok().entity("two").build();
        }
    }

    @Path("three")
    private static class TestResourceThree {

        @Path("/endpoint3")
        @GET
        public Response endpoint3() {
            return Response.ok().entity("three").build();
        }
    }

    @Path("four")
    private static class TestResourceFour {

        @Path("/endpoint4")
        @GET
        public Response endpoint4() {
            return Response.ok().entity("four").build();
        }
    }
}
