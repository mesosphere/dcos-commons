package com.mesosphere.sdk.queues.scheduler;

import com.mesosphere.sdk.testing.*;
import org.junit.Test;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        // Just validate universe templating:
        new ServiceTestRunner("svc1.yml").run();
    }
}
