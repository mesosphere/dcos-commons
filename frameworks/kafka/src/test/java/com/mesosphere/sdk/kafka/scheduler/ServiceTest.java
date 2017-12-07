package com.mesosphere.sdk.kafka.scheduler;

import org.junit.Test;

import com.mesosphere.sdk.testing.ServiceTestRunner;

import java.util.HashMap;
import java.util.Map;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("KAFKA_ZOOKEEPER_URI", "/path/to/zk"); // set by our Main.java
        map.put("SETUP_HELPER_ADVERTISED_LISTENERS", "advertised.listeners=FAKE"); // set by setup-helper
        map.put("SETUP_HELPER_LISTENERS", "listeners=FAKE"); // set by setup-helper
        map.put("SETUP_HELPER_SECURITY_INTER_BROKER_PROTOCOL", "fake"); // set by setup-helper
        map.put("SETUP_HELPER_SUPER_USERS", "User:fake"); // set by setup-helper

        new ServiceTestRunner()
                .setPodEnv("kafka", map)
                .run();
    }
}
