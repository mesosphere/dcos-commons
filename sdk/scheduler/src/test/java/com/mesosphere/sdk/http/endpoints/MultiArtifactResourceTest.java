package com.mesosphere.sdk.http.endpoints;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.scheduler.SchedulerConfig;

public class MultiArtifactResourceTest {

    @Mock private SchedulerConfig mockSchedulerConfig;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockSchedulerConfig.getApiServerPort()).thenReturn(1234);
        when(mockSchedulerConfig.getAutoipTLD()).thenReturn("some.tld");
        when(mockSchedulerConfig.getMarathonName()).thenReturn("test-marathon");
    }

    @Test
    public void testGetQueuesTemplateUrl() {
        UUID uuid = UUID.randomUUID();
        assertEquals("http://fwk-name.test-marathon.some.tld:1234/v1/service/job-name/artifacts/template/"
                        + uuid.toString() + "/some-pod/some-task/some-config",
                MultiArtifactResource.getUrlFactory("fwk-name", "job-name", mockSchedulerConfig).get(uuid, "some-pod", "some-task", "some-config"));
    }

    @Test
    public void testGetQueuesTemplateUrlSlashed() {
        UUID uuid = UUID.randomUUID();
        assertEquals(
                "http://fwk-name-to-path.test-marathon.some.tld:1234/v1/service/path.to.job-name/artifacts/template/"
                        + uuid.toString() + "/some-pod/some-task/some-config",
                MultiArtifactResource.getUrlFactory("/path/to/fwk-name", "/path/to/job-name", mockSchedulerConfig).get(uuid, "some-pod", "some-task", "some-config"));
    }
}
