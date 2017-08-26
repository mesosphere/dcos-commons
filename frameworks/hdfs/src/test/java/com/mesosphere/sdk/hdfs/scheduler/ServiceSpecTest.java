package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.specification.yaml.TemplateUtils;
import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class ServiceSpecTest extends BaseServiceSpecTest {

    public ServiceSpecTest() {
        super();
    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("svc.yml");
    }

    @Test
    public void testRenderHdfsSiteXml() throws IOException {
        renderTemplate(System.getProperty("user.dir") + "/src/main/dist/hdfs-site.xml");
    }

    @Test
    public void testRenderCoreSiteXml() throws IOException {
        renderTemplate(System.getProperty("user.dir") + "/src/main/dist/core-site.xml");
    }

    private void renderTemplate(String pathStr) throws IOException {
        String fileStr = new String(Files.readAllBytes(Paths.get(pathStr)), StandardCharsets.UTF_8);

        // Reproduction of what's added to tasks automatically, and in Main.java:
        Map<String, String> updatedEnv = new TaskEnvRouter(envVars).getConfig("ALL");
        updatedEnv.put(EnvConstants.FRAMEWORK_HOST_TASKENV, "hdfs.on.some.cluster");
        updatedEnv.put("MESOS_SANDBOX", "/mnt/sandbox");
        updatedEnv.put("SERVICE_ZK_ROOT", "/dcos-service-path__to__hdfs");

        // Throw when a value is missing:
        TemplateUtils.applyEnvToMustache(pathStr, fileStr, updatedEnv, TemplateUtils.MissingBehavior.EXCEPTION);
    }
}
