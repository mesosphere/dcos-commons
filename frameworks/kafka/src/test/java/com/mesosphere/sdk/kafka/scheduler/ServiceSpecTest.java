package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.BeforeClass;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {
    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.put("FRAMEWORK_NAME", "kafka");
        ENV_VARS.put("CONFIG_TEMPLATE_PATH","frameworks/kafka");
        ENV_VARS.put("KAFKA_URI", "some_uri");
        ENV_VARS.put("BOOTSTRAP_URI", "another_uri");
        ENV_VARS.put("PLACEMENT_CONSTRAINTS","");
        ENV_VARS.put("PHASE_STRATEGY","serial");
        ENV_VARS.put("KAFKA_VERSION_PATH","somepath");
        ENV_VARS.put("PORT_API", "8080");
        ENV_VARS.put("BROKER_COUNT", "2");
        ENV_VARS.put("BROKER_CPUS", "0.1");
        ENV_VARS.put("BROKER_MEM", "512");
        ENV_VARS.put("BROKER_DISK_SIZE", "5000");
        ENV_VARS.put("BROKER_DISK_TYPE", "ROOT");
        ENV_VARS.put("BROKER_DISK_PATH", "path");
        ENV_VARS.put("PORT_BROKER_PORT","9999");
        ENV_VARS.put("BROKER_PORT","0");
    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("svc.yml");
    }
}
