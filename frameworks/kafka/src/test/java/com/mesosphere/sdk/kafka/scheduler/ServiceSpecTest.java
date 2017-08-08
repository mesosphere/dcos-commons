package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    public ServiceSpecTest() {
        super(
                "FRAMEWORK_NAME", "kafka",
                "CONFIG_TEMPLATE_PATH", "frameworks/kafka",
                "KAFKA_URI", "some_uri",
                "BOOTSTRAP_URI", "another_uri",
                "PLACEMENT_CONSTRAINTS","",
                "PHASE_STRATEGY","serial",
                "KAFKA_VERSION_PATH","somepath",
                "PORT_API", "8080",
                "BROKER_COUNT", "2",
                "BROKER_CPUS", "0.1",
                "BROKER_MEM", "512",
                "BROKER_DISK_SIZE", "5000",
                "BROKER_DISK_TYPE", "ROOT",
                "BROKER_DISK_PATH", "path",
                "PORT_BROKER_PORT", "9999",
                "BROKER_PORT", "0");
    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("svc.yml");
    }

}
