package com.mesosphere.sdk.http.endpoints;

import org.junit.Test;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class ArtifactResourceTest {

    @Test
    public void testGetStandaloneTemplateUrl() {
        UUID uuid = UUID.randomUUID();
        assertEquals(
                "http://api.svc-name.marathon.l4lb.thisdcos.directory/v1/artifacts/template/"
                        + uuid.toString() + "/some-pod/some-task/some-config",
                ArtifactResource.getStandaloneServiceTemplateUrl("svc-name", uuid, "some-pod", "some-task", "some-config"));
        assertEquals(
                "http://api.pathtosvc-name.marathon.l4lb.thisdcos.directory/v1/artifacts/template/"
                        + uuid.toString() + "/some-pod/some-task/some-config",
                ArtifactResource.getStandaloneServiceTemplateUrl("/path/to/svc-name", uuid, "some-pod", "some-task", "some-config"));
    }

    @Test
    public void testGetQueuesTemplateUrl() {
        UUID uuid = UUID.randomUUID();
        assertEquals(
                "http://api.svc-name.marathon.l4lb.thisdcos.directory/v1/jobs/job-name/artifacts/template/"
                        + uuid.toString() + "/some-pod/some-task/some-config",
                ArtifactResource.getJobTemplateUrl("svc-name", "job-name", uuid, "some-pod", "some-task", "some-config"));
        assertEquals(
                // TODO(nickbp): figure something out for slashes in job names, or just disallow them...
                "http://api.pathtosvc-name.marathon.l4lb.thisdcos.directory/v1/jobs//path/to/job-name/artifacts/template/"
                        + uuid.toString() + "/some-pod/some-task/some-config",
                ArtifactResource.getJobTemplateUrl("/path/to/svc-name", "/path/to/job-name", uuid, "some-pod", "some-task", "some-config"));
    }
}
