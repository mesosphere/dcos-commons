package com.mesosphere.sdk.http.endpoints;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.scheduler.SchedulerConfig;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class ArtifactResourceTest {

    @Mock SchedulerConfig mockSchedulerConfig;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockSchedulerConfig.getApiServerPort()).thenReturn(1234);
        when(mockSchedulerConfig.getServiceTLD()).thenReturn("some.tld");
    }

    @Test
    public void testGetStandaloneTemplateUrl() {
        UUID uuid = UUID.randomUUID();
        assertEquals(
                "http://svc-name.marathon.some.tld:1234/v1/artifacts/template/"
                        + uuid.toString() + "/some-pod/some-task/some-config",
                ArtifactResource.getUrlFactory("svc-name", mockSchedulerConfig).get(uuid, "some-pod", "some-task", "some-config"));
        assertEquals(
                "http://pathtosvc-name.marathon.some.tld:1234/v1/artifacts/template/"
                        + uuid.toString() + "/some-pod/some-task/some-config",
                ArtifactResource.getUrlFactory("/path/to/svc-name", mockSchedulerConfig).get(uuid, "some-pod", "some-task", "some-config"));
    }
}
