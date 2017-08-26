package com.mesosphere.sdk.template.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    public ServiceSpecTest() {
        super();
    }

    @Test
    public void testYmlBase() throws Exception {
        testYaml("svc.yml");
    }
}
