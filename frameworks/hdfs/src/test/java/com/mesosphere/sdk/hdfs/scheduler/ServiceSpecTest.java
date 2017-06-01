package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.config.DefaultTaskEnvRouter;
import com.mesosphere.sdk.specification.yaml.TemplateUtils;
import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ServiceSpecTest extends BaseServiceSpecTest {

    public ServiceSpecTest() {
        super(
                "PORT_API", "8080",
                "HDFS_VERSION", "hadoop-2.6.0-cdh5.9.1",
                "FRAMEWORK_NAME", "hdfs",
                "SERVICE_PRINCIPAL", "hdfs-principal",
                "JOURNAL_CPUS", "1.0",
                "JOURNAL_MEM", "256",
                "JOURNAL_DISK", "5000",
                "JOURNAL_DISK_TYPE", "ROOT",
                "JOURNAL_STRATEGY", "parallel",
                "NAME_CPUS", "1.0",
                "NAME_MEM", "256",
                "NAME_DISK", "5000",
                "NAME_DISK_TYPE", "ROOT",
                "ZKFC_CPUS", "1.0",
                "ZKFC_MEM", "256",
                "DATA_COUNT", "3",
                "DATA_CPUS", "1.0",
                "DATA_MEM", "256",
                "DATA_DISK", "5000",
                "DATA_DISK_TYPE", "ROOT",
                "DATA_STRATEGY", "parallel",
                "EXECUTOR_URI", "",
                "LIBMESOS_URI", "",
                "HDFS_URI", "",
                "BOOTSTRAP_URI", "",
                "TASKCFG_ALL_ADMINISTRATORS", "core,centos,azureuser",
                "TASKCFG_ALL_NAME_NODE_RPC_PORT","9001",
                "TASKCFG_ALL_NAME_NODE_HTTP_PORT","9002",
                "TASKCFG_ALL_ZKFC_PORT","8019",
                "TASKCFG_ALL_JOURNAL_NODE_RPC_PORT","8485",
                "TASKCFG_ALL_JOURNAL_NODE_HTTP_PORT","8480",
                "TASKCFG_ALL_DATA_NODE_RPC_PORT","9003",
                "TASKCFG_ALL_DATA_NODE_HTTP_PORT","9004",
                "TASKCFG_ALL_DATA_NODE_IPC_PORT","9005",
                "TASKCFG_ALL_DATA_NODE_BALANCE_BANDWIDTH_PER_SEC","41943040",
                "TASKCFG_ALL_NAME_NODE_SAFEMODE_THRESHOLD_PCT","0.9",
                "TASKCFG_ALL_DATA_NODE_HANDLER_COUNT","10",
                "TASKCFG_ALL_NAME_NODE_HANDLER_COUNT","20",
                "TASKCFG_ALL_PERMISSIONS_ENABLED","false",
                "TASKCFG_ALL_NAME_NODE_HEARTBEAT_RECHECK_INTERVAL","60000",
                "TASKCFG_ALL_IMAGE_COMPRESS","true",
                "TASKCFG_ALL_IMAGE_COMPRESSION_CODEC","org.apache.hadoop.io.compress.SnappyCodec",
                "TASKCFG_ALL_NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION","0.95",
                "TASKCFG_ALL_NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION","4",
                "TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT","true",
                "TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_PATH","/var/lib/hadoop-hdfs/dn_socket",
                "TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE","1000",
                "TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS","1000",
                "TASKCFG_ALL_NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK", "false",
                "TASKCFG_ALL_HA_FENCING_METHODS", "shell(/bin/true)",
                "TASKCFG_ALL_HA_AUTOMATIC_FAILURE", "true",
                "TASKCFG_ALL_CLIENT_FAILOVER_PROXY_PROVIDER_HDFS", "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider",
                "TASKCFG_ALL_HADOOP_PROXYUSER_HUE_HOSTS", "*",
                "TASKCFG_ALL_HADOOP_PROXYUSER_HUE_GROUPS", "*",
                "TASKCFG_ALL_HADOOP_PROXYUSER_ROOT_HOSTS", "*",
                "TASKCFG_ALL_HADOOP_PROXYUSER_ROOT_GROUPS", "*",
                "TASKCFG_ALL_HADOOP_PROXYUSER_HTTPFS_GROUPS", "*",
                "TASKCFG_ALL_HADOOP_PROXYUSER_HTTPFS_HOSTS", "*");
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
        Path path = Paths.get(pathStr);
        byte[] bytes = Files.readAllBytes(path);
        String fileStr = new String(bytes, Charset.defaultCharset());
        Map<String, String> updatedEnv = new HashMap<>(new DefaultTaskEnvRouter(envVars).getConfig("ALL"));

        String renderedFileStr = TemplateUtils.applyEnvToMustache(fileStr, updatedEnv);
        Assert.assertEquals(renderedFileStr, -1, renderedFileStr.indexOf("<value></value>"));
        Assert.assertTrue(TemplateUtils.isMustacheFullyRendered(renderedFileStr));
    }
}
