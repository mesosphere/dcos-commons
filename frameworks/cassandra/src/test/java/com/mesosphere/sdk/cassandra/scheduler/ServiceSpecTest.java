package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    public ServiceSpecTest() {
        super();
    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("svc.yml");
    }
}
