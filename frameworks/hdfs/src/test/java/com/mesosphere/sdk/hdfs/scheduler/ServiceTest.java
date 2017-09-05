package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.specification.yaml.TemplateUtils;
import com.mesosphere.sdk.testing.CosmosRenderer;
import com.mesosphere.sdk.testing.ServiceTestBuilder;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        new ServiceTestBuilder().render();
    }

    @Test
    public void testRenderHdfsSiteXml() throws IOException {
        renderEndpointTemplate(ServiceTestBuilder.getDistFile("hdfs-site.xml"));
    }

    @Test
    public void testRenderCoreSiteXml() throws IOException {
        renderEndpointTemplate(ServiceTestBuilder.getDistFile("core-site.xml"));
    }

    private void renderEndpointTemplate(File templateFile) throws IOException {
        String fileStr = new String(Files.readAllBytes(templateFile.toPath()), StandardCharsets.UTF_8);

        Map<String, String> schedulerEnv =
                CosmosRenderer.renderSchedulerEnvironment(Collections.emptyMap(), Collections.emptyMap());
        Map<String, String> taskEnv = new TaskEnvRouter(schedulerEnv).getConfig("ALL");
        // The templates also expect the following values, which are normally added to tasks automatically by Mesos, and
        // by HDFS' Main.java:
        taskEnv.put(EnvConstants.FRAMEWORK_HOST_TASKENV, "hdfs.on.some.cluster");
        taskEnv.put("MESOS_SANDBOX", "/mnt/sandbox");
        taskEnv.put("SERVICE_ZK_ROOT", "/dcos-service-path__to__hdfs");

        // Validate by throwing if any values are missing:
        TemplateUtils.applyEnvToMustache(
                templateFile.getName(), fileStr, taskEnv, TemplateUtils.MissingBehavior.EXCEPTION);
    }
}
