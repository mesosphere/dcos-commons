package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.config.DefaultTaskEnvRouter;
import com.mesosphere.sdk.specification.yaml.TemplateUtils;
import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ServiceSpecTest extends BaseServiceSpecTest {

    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.put("PORT_API", "8080");
        ENV_VARS.put("HDFS_VERSION", "hadoop-2.6.0-cdh5.9.1");
        ENV_VARS.put("FRAMEWORK_NAME", "hdfs");
        ENV_VARS.put("SERVICE_PRINCIPAL", "hdfs-principal");
        ENV_VARS.put("JOURNAL_CPUS", "1.0");
        ENV_VARS.put("JOURNAL_MEM", "256");
        ENV_VARS.put("JOURNAL_DISK", "5000");
        ENV_VARS.put("JOURNAL_DISK_TYPE", "ROOT");
        ENV_VARS.put("JOURNAL_STRATEGY", "parallel");
        ENV_VARS.put("NAME_CPUS", "1.0");
        ENV_VARS.put("NAME_MEM", "256");
        ENV_VARS.put("NAME_DISK", "5000");
        ENV_VARS.put("NAME_DISK_TYPE", "ROOT");
        ENV_VARS.put("ZKFC_CPUS", "1.0");
        ENV_VARS.put("ZKFC_MEM", "256");
        ENV_VARS.put("DATA_COUNT", "3");
        ENV_VARS.put("DATA_CPUS", "1.0");
        ENV_VARS.put("DATA_MEM", "256");
        ENV_VARS.put("DATA_DISK", "5000");
        ENV_VARS.put("DATA_DISK_TYPE", "ROOT");
        ENV_VARS.put("DATA_STRATEGY", "parallel");
        ENV_VARS.put("EXECUTOR_URI", "");
        ENV_VARS.put("LIBMESOS_URI", "");
        ENV_VARS.put("HDFS_URI", "");
        ENV_VARS.put("BOOTSTRAP_URI", "");
        ENV_VARS.put("TASKCFG_ALL_ADMINISTRATORS", "core,centos,azureuser");
        ENV_VARS.put("TASKCFG_ALL_NAME_NODE_RPC_PORT","9001");
        ENV_VARS.put("TASKCFG_ALL_NAME_NODE_HTTP_PORT","9002");
        ENV_VARS.put("TASKCFG_ALL_ZKFC_PORT","8019");
        ENV_VARS.put("TASKCFG_ALL_JOURNAL_NODE_RPC_PORT","8485");
        ENV_VARS.put("TASKCFG_ALL_JOURNAL_NODE_HTTP_PORT","8480");
        ENV_VARS.put("TASKCFG_ALL_DATA_NODE_RPC_PORT","9003");
        ENV_VARS.put("TASKCFG_ALL_DATA_NODE_HTTP_PORT","9004");
        ENV_VARS.put("TASKCFG_ALL_DATA_NODE_IPC_PORT","9005");
        ENV_VARS.put("TASKCFG_ALL_DATA_NODE_BALANCE_BANDWIDTH_PER_SEC","41943040");
        ENV_VARS.put("TASKCFG_ALL_NAME_NODE_SAFEMODE_THRESHOLD_PCT","0.9");
        ENV_VARS.put("TASKCFG_ALL_DATA_NODE_HANDLER_COUNT","10");
        ENV_VARS.put("TASKCFG_ALL_NAME_NODE_HANDLER_COUNT","20");
        ENV_VARS.put("TASKCFG_ALL_PERMISSIONS_ENABLED","false");
        ENV_VARS.put("TASKCFG_ALL_NAME_NODE_HEARTBEAT_RECHECK_INTERVAL","60000");
        ENV_VARS.put("TASKCFG_ALL_IMAGE_COMPRESS","true");
        ENV_VARS.put("TASKCFG_ALL_IMAGE_COMPRESSION_CODEC","org.apache.hadoop.io.compress.SnappyCodec");
        ENV_VARS.put("TASKCFG_ALL_NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION","0.95");
        ENV_VARS.put("TASKCFG_ALL_NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION","4");
        ENV_VARS.put("TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT","true");
        ENV_VARS.put("TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_PATH","/var/lib/hadoop-hdfs/dn_socket");
        ENV_VARS.put("TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE","1000");
        ENV_VARS.put("TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS","1000");
        ENV_VARS.put("TASKCFG_ALL_NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK", "false");
        ENV_VARS.put("TASKCFG_ALL_HA_FENCING_METHODS", "shell(/bin/true)");
        ENV_VARS.put("TASKCFG_ALL_HA_AUTOMATIC_FAILURE", "true");
        ENV_VARS.put("TASKCFG_ALL_CLIENT_FAILOVER_PROXY_PROVIDER_HDFS", "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider");
        ENV_VARS.put("TASKCFG_ALL_HADOOP_PROXYUSER_HUE_HOSTS", "*");
        ENV_VARS.put("TASKCFG_ALL_HADOOP_PROXYUSER_HUE_GROUPS", "*");
        ENV_VARS.put("TASKCFG_ALL_HADOOP_PROXYUSER_ROOT_HOSTS", "*");
        ENV_VARS.put("TASKCFG_ALL_HADOOP_PROXYUSER_ROOT_GROUPS", "*");
        ENV_VARS.put("TASKCFG_ALL_HADOOP_PROXYUSER_HTTPFS_GROUPS", "*");
        ENV_VARS.put("TASKCFG_ALL_HADOOP_PROXYUSER_HTTPFS_HOSTS", "*");
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
        Map<String, String> updatedEnv = new HashMap<>(new DefaultTaskEnvRouter(ENV_VARS).getConfig("ALL"));

        String renderedFileStr = TemplateUtils.applyEnvToMustache(fileStr, updatedEnv);
        Assert.assertEquals(renderedFileStr, -1, renderedFileStr.indexOf("<value></value>"));
        Assert.assertTrue(TemplateUtils.isMustacheFullyRendered(renderedFileStr));
    }
}
