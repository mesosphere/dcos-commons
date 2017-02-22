package com.mesosphere.sdk.rethinkdb.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.BeforeClass;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.set("EXECUTOR_URI", "");
        ENV_VARS.set("LIBMESOS_URI", "");
        ENV_VARS.set("PORT_API", "8080");
        ENV_VARS.set("FRAMEWORK_NAME", "rethinkdb");

        ENV_VARS.set("RETHINKDB_COUNT", "2");
        ENV_VARS.set("RETHINKDB_CPUS", "0.1");
        ENV_VARS.set("RETHINKDB_MEM", "512");
        ENV_VARS.set("RETHINKDB_DISK", "5000");

        ENV_VARS.set("SLEEP_DURATION", "1000");
    }

    @Test
    public void testYmlBase() throws Exception {
        testYaml("svc.yml");
    }
}
