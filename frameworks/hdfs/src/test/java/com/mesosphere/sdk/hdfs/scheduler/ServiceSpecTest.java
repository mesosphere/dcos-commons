package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.BeforeClass;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.set("EXECUTOR_URI", "");
        ENV_VARS.set("LIBMESOS_URI", "");
        ENV_VARS.set("PORT0", "8080");
        ENV_VARS.set("SERVICE_NAME", "hdfs");
        ENV_VARS.set("SERVICE_PRINCIPAL", "principal");
        ENV_VARS.set("JOURNAL_CPUS", "1.0");
        ENV_VARS.set("JOURNAL_MEM", "1024");
        ENV_VARS.set("JOURNAL_DISK", "1024");
        ENV_VARS.set("JOURNAL_DISK_TYPE", "MOUNT");
        ENV_VARS.set("JOURNAL_NODE_RPC_PORT", "1");
        ENV_VARS.set("JOURNAL_NODE_HTTP_PORT", "1");
        ENV_VARS.set("ZKFC_CPUS", "1.0");
        ENV_VARS.set("ZKFC_MEM", "1024");
        ENV_VARS.set("NAME_CPUS", "1.0");
        ENV_VARS.set("NAME_MEM", "1024");
        ENV_VARS.set("NAME_DISK", "1024");
        ENV_VARS.set("NAME_DISK_TYPE", "MOUNT");
        ENV_VARS.set("NAME_NODE_RPC_PORT", "1");
        ENV_VARS.set("NAME_NODE_HTTP_PORT", "1");
        ENV_VARS.set("DATA_COUNT", "3");
        ENV_VARS.set("DATA_CPUS", "1.0");
        ENV_VARS.set("DATA_MEM", "1024");
        ENV_VARS.set("DATA_DISK", "1024");
        ENV_VARS.set("DATA_DISK_TYPE", "MOUNT");
        ENV_VARS.set("DATA_NODE_RPC_PORT", "1");
        ENV_VARS.set("DATA_NODE_HTTP_PORT", "1");
        ENV_VARS.set("DATA_NODE_IPC_PORT", "1");
        ENV_VARS.set("JOURNAL_STRATEGY", "parallel");
        ENV_VARS.set("DATA_STRATEGY", "parallel");
    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("hdfs_svc.yml");
    }
}
