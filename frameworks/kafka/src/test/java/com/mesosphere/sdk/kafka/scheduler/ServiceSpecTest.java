package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    public ServiceSpecTest() {
        super(
                "FRAMEWORK_NAME", "kafka",
                "CONFIG_TEMPLATE_PATH", "frameworks/kafka",
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
                "BROKER_PORT", "0",
                "KAFKA_URI", "http://kafka.uri",
                "KAFKA_JAVA_URI", "http://kafka-java.uri",
                "BOOTSTRAP_URI", "http://bootstrap.uri",
                "EXECUTOR_URI", "http://executor.uri",
                "LIBMESOS_URI", "http://libmesos.uri",
                "KAFKA_STATSD_URI", "http://kafka-statsd.uri",
                "CLIENT_STATSD_URI", "http://client-statsd.uri");
    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("svc.yml");
    }
}
