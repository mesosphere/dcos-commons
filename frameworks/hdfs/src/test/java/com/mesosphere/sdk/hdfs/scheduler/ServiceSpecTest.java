package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.specification.yaml.TemplateUtils;
import com.mesosphere.sdk.testing.ServiceRenderUtils;
import com.mesosphere.sdk.testing.ServiceSpecTestUtils;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;

public class ServiceSpecTest {

    @Test
    public void testSpec() throws Exception {
        ServiceSpecTestUtils.test();
    }

    @Test
    public void testRenderHdfsSiteXml() throws IOException {
        renderEndpointTemplate(ServiceRenderUtils.getDistFile("hdfs-site.xml"));
    }

    @Test
    public void testRenderCoreSiteXml() throws IOException {
        renderEndpointTemplate(ServiceRenderUtils.getDistFile("core-site.xml"));
    }

    private void renderEndpointTemplate(File templateFile) throws IOException {
        String fileStr = new String(Files.readAllBytes(templateFile.toPath()), StandardCharsets.UTF_8);

        // Reproduction of what's added to tasks automatically, and in Main.java:
        Map<String, String> updatedEnv = new TaskEnvRouter(
                ServiceRenderUtils.renderSchedulerEnvironment(Collections.emptyMap())).getConfig("ALL");
        updatedEnv.put(EnvConstants.FRAMEWORK_HOST_TASKENV, "hdfs.on.some.cluster");
        updatedEnv.put("MESOS_SANDBOX", "/mnt/sandbox");
        updatedEnv.put("SERVICE_ZK_ROOT", "/dcos-service-path__to__hdfs");

        // Validate by throwing if any values are missing:
        TemplateUtils.applyEnvToMustache(
                templateFile.getName(), fileStr, updatedEnv, TemplateUtils.MissingBehavior.EXCEPTION);
    }
}
