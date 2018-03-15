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
                        ArtifactResource.getUrlFactory("svc-name").get(uuid, "some-pod", "some-task", "some-config"));
        assertEquals(
                "http://api.pathtosvc-name.marathon.l4lb.thisdcos.directory/v1/artifacts/template/"
                        + uuid.toString() + "/some-pod/some-task/some-config",
                        ArtifactResource.getUrlFactory("/path/to/svc-name").get(uuid, "some-pod", "some-task", "some-config"));
    }
}
