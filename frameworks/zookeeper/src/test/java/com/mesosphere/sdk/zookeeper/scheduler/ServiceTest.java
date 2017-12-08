package com.mesosphere.sdk.zookeeper.scheduler;

import com.mesosphere.sdk.testing.ServiceTestRunner;
import org.junit.Test;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        new ServiceTestRunner()
            .setPodEnv("zookeeper", "ZOOKEEPER_SERVERS", "zk1,zk2,zk3")
            .run();
    }
}
