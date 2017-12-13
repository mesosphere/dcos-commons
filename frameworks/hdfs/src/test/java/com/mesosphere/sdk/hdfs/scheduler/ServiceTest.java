package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.specification.yaml.RawPort;
import com.mesosphere.sdk.specification.yaml.TemplateUtils;
import com.mesosphere.sdk.testing.CosmosRenderer;
import com.mesosphere.sdk.testing.ServiceTestRunner;

import com.mesosphere.sdk.testing.ServiceTestResult;
import org.junit.Assert;
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
        // Our Main.java only defines SERVICE_ZK_ROOT in our name nodes.
        // However, the test utilities are strict about missing template params so we set something for all pods:
        new ServiceTestRunner()
                .setPodEnv("journal", "SERVICE_ZK_ROOT", "")
                .setPodEnv("data", "SERVICE_ZK_ROOT", "")
                .setPodEnv("name", "SERVICE_ZK_ROOT", "/path/to/zk")
                .run();
    }

    @Test
    public void testTLS() throws Exception {
        ServiceTestResult result = new ServiceTestRunner()
                .setPodEnv("journal", "SERVICE_ZK_ROOT", "")
                .setPodEnv("data", "SERVICE_ZK_ROOT", "")
                .setPodEnv("name", "SERVICE_ZK_ROOT", "/path/to/zk")
                .setOptions(
                        "service.security.transport_encryption.enabled", "true",
                        "hdfs.name_node_https_port", "2000",
                        "hdfs.journal_node_https_port", "2001",
                        "hdfs.data_node_https_port", "2002")
                .run();

        RawPort nameHttpsPort = result
                .getRawServiceSpec()
                .getPods()
                .get("name")
                .getResourceSets()
                .get("name-resources")
                .getPorts()
                .get("name-https");
        Assert.assertNotNull(nameHttpsPort);
        Assert.assertEquals(2000, nameHttpsPort.getPort().intValue());
        String config = result.getTaskConfig("name", "node", "hdfs-site");
        Assert.assertTrue(config.contains("dfs.namenode.https-address.hdfs.name-0-node"));
        Assert.assertTrue(config.contains("dfs.namenode.https-address.hdfs.name-1-node"));

        RawPort journalHttpsPort = result
                .getRawServiceSpec()
                .getPods()
                .get("journal")
                .getResourceSets()
                .get("journal-resources")
                .getPorts()
                .get("journal-https");
        Assert.assertNotNull(journalHttpsPort);
        Assert.assertEquals(2001, journalHttpsPort.getPort().intValue());
        Assert.assertTrue(config.contains("0.0.0.0:2001"));
        Assert.assertTrue(config.contains("dfs.journalnode.https-address"));

        RawPort dataHttpsPort = result
                .getRawServiceSpec()
                .getPods()
                .get("data")
                .getTasks()
                .get("node")
                .getPorts()
                .get("data-https");
        Assert.assertNotNull(dataHttpsPort);
        Assert.assertEquals(2002, dataHttpsPort.getPort().intValue());
        Assert.assertTrue(config.contains("0.0.0.0:2002"));
        Assert.assertTrue(config.contains("dfs.datanode.https.address"));
    }

    @Test
    public void testRenderHdfsSiteXml() throws IOException {
        renderEndpointTemplate(ServiceTestRunner.getDistFile(Main.HDFS_SITE_XML));
    }

    @Test
    public void testRenderCoreSiteXml() throws IOException {
        renderEndpointTemplate(ServiceTestRunner.getDistFile(Main.CORE_SITE_XML));
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
        taskEnv.put(Main.SERVICE_ZK_ROOT_TASKENV, "/dcos-service-path__to__hdfs");

        // Validate by throwing if any values are missing:
        TemplateUtils.renderMustacheThrowIfMissing(templateFile.getName(), fileStr, taskEnv);
    }
}
