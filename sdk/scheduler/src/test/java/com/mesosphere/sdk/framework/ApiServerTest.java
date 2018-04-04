package com.mesosphere.sdk.framework;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.ContentResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHttpRequest;
import org.junit.Assert;
import org.junit.Test;

import com.mesosphere.sdk.scheduler.SchedulerConfig;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ApiServerTest {
    private static final int SHORT_TIMEOUT_MILLIS = 100;
    private static final int LONG_TIMEOUT_MILLIS = 30000;

    @Test
    public void testApiServerEndpointHandling() throws Exception {
        SchedulerConfig mockSchedulerConfig = mock(SchedulerConfig.class);
        when(mockSchedulerConfig.getApiServerInitTimeout()).thenReturn(Duration.ofMillis(LONG_TIMEOUT_MILLIS));
        when(mockSchedulerConfig.getApiServerPort()).thenReturn(0);

        Listener listener = new Listener();
        ApiServer server = ApiServer.start(
                mockSchedulerConfig,
                Arrays.asList(
                        new TestResourcePlans(),
                        new TestResourcePod(),
                        new TestResourceMultiPlans(),
                        new TestResourceMultiPod()),
                listener);
        listener.waitForStarted();

        Map<String, String> expectedEndpoints = new HashMap<>();
        expectedEndpoints.put("/v1/metrics", "");
        expectedEndpoints.put("/v1/metrics/prometheus", "");

        expectedEndpoints.put("/v1/plans/foo", "Service Plan: foo");
        expectedEndpoints.put("/v1/plans/bar", "Service Plan: bar");

        expectedEndpoints.put("/v1/pod/foo/info", "Service Pod: foo");
        expectedEndpoints.put("/v1/pod/bar/info", "Service Pod: bar");

        expectedEndpoints.put("/v1/service/fast/plans/foo", "fast Plan: foo");
        expectedEndpoints.put("/v1/service/slow/plans/bar", "slow Plan: bar");
        expectedEndpoints.put("/v1/service/path/to/svc/plans/foo", null); // slashes in service name not supported

        expectedEndpoints.put("/v1/service/fast/pod/foo/info", "fast Pod: foo");
        expectedEndpoints.put("/v1/service/slow/pod/foo/info", "slow Pod: foo");
        expectedEndpoints.put("/v1/service/path/to/svc/pod/foo/info", null); // slashes in service name not supported

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

    @Path("/v1/plans")
    public static class TestResourcePlans {

        @Path("{planName}")
        @GET
        public Response getPlan(@PathParam("planName") String planName) {
            return Response.ok().entity("Service Plan: " + planName).build();
        }
    }

    @Path("/v1/pod")
    public static class TestResourcePod {

        @Path("/{name}/info")
        @GET
        public Response getPlan(@PathParam("name") String podName) {
            return Response.ok().entity("Service Pod: " + podName).build();
        }
    }

    @Path("/v1/service")
    public static class TestResourceMultiPlans {

        @Path("{serviceName}/plans/{planName}")
        @GET
        public Response getPlan(@PathParam("serviceName") String serviceName, @PathParam("planName") String planName) {
            return Response.ok().entity(serviceName + " Plan: " + planName).build();
        }
    }

    @Path("/v1/service")
    public static class TestResourceMultiPod {

        @Path("/{serviceName}/pod/{name}/info")
        @GET
        public Response getPlan(@PathParam("serviceName") String serviceName, @PathParam("name") String podName) {
            return Response.ok().entity(serviceName + " Pod: " + podName).build();
        }
    }
}
