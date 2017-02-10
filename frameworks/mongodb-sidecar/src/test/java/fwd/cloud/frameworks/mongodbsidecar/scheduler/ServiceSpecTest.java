package fwd.cloud.frameworks.mongodbsidecar.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.BeforeClass;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.set("EXECUTOR_URI", "");
        ENV_VARS.set("LIBMESOS_URI", "");
        ENV_VARS.set("PORT_API", "8080");
        ENV_VARS.set("FRAMEWORK_NAME", "mongodb-sidecar");
        ENV_VARS.set("SERVICE_PRINCIPAL", "mongodb-sidecar-principal");
        ENV_VARS.set("MONGODB_PORT", "27017");
        ENV_VARS.set("MONGODB_REST_PORT", "28017");
        ENV_VARS.set("MONGODB_COUNT", "3");
        ENV_VARS.set("MONGODB_CPUS", "0.1");
        ENV_VARS.set("MONGODB_MEM", "1024");
        ENV_VARS.set("MONGODB_DISK", "1000");
        ENV_VARS.set("SIDECAR_COUNT", "1");
        ENV_VARS.set("SIDECAR_CPUS", "0.2");
        ENV_VARS.set("SIDECAR_MEM", "128");
        ENV_VARS.set("SIDECAR_DISK", "0");
        ENV_VARS.set("SIDECAR_APP_NAME", "blub");
        ENV_VARS.set("SIDECAR_ADD_DELAY", "10");
        ENV_VARS.set("SIDECAR_IMAGE", "alpine");
        ENV_VARS.set("SIDECAR_CMD", "sleep 100");
        ENV_VARS.set("SIDECAR_ZK_URL", "zk://zk-1.zk:2181");
        ENV_VARS.set("SIDECAR_MESOS_URL", "http://master.mezos/mesos");
    }

    @Test
    public void testYmlBase() throws Exception {
        testYaml("svc.yml");
    }
}
