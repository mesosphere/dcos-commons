package com.mesosphere.sdk.http.endpoints;

import org.junit.Test;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class ArtifactResourceTest {

    @Test
    public void testGetTemplateUrl() {
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
}
