package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.BeforeClass;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {
    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.set("FRAMEWORK_NAME", "kafka");
        ENV_VARS.set("FRAMEWORK_PRINCIPLE","kafka-principle");
        ENV_VARS.set("MESOS_ZOOKEEPER_URI","master.mesos:2811");
        ENV_VARS.set("CONFIG_TEMPLATE_PATH","frameworks/kafka");
        ENV_VARS.set("KAFKA_URI", "some_uri");
        ENV_VARS.set("BOOTSTRAP_URI", "another_uri");
        ENV_VARS.set("PLACEMENT_CONSTRAINTS","");
        ENV_VARS.set("PHASE_STRATEGY","serial");
        ENV_VARS.set("KAFKA_VERSION_PATH","somepath");
        ENV_VARS.set("PORT_API", "8080");
        ENV_VARS.set("BROKER_COUNT", "2");
        ENV_VARS.set("BROKER_CPUS", "0.1");
        ENV_VARS.set("BROKER_MEM", "512");
        ENV_VARS.set("BROKER_DISK_SIZE", "5000");
        ENV_VARS.set("BROKER_DISK_TYPE", "ROOT");
        ENV_VARS.set("BROKER_DATA_PATH", "ROOT");
        ENV_VARS.set("PORT_BROKER_PORT","9999");
        ENV_VARS.set("BROKER_PORT","0");

    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("svc.yml");
    }
}
