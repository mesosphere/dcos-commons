package com.mesosphere.sdk.sdkspark.scheduler;

import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Assert;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    public ServiceSpecTest() {
        super(
                "EXECUTOR_URI", "",
                "LIBMESOS_URI", "",
                "PORT_API", "8080",
                "FRAMEWORK_NAME", "sdkspark",
                "SPARK_DOCKER_IMAGE", "artrand/spark",


                "EXECUTOR_COUNT", "2",
                "EXECUTOR_CORES", "1",
                "EXECUTOR_MEM", "512",
                "EXECUTOR_DISK", "5000",
                "EXECUTOR_DISK_TYPE", "ROOT",

                "COORDINATOR_COUNT", "2",
                "COORDINATOR_CORES", "1",
                "COORDINATOR_MEM", "512",
                "COORDINATOR_DISK", "5000",
                "COORDINATOR_DISK_TYPE", "ROOT",
                "SPARK_APP_URL", "http://someplace/mysparkapp.jar",
                "SLEEP_DURATION", "1000");
    }

    @Test
    public void testYmlBase() throws Exception {
        testYaml("svc.yml");
    }

    public void testSparkScheduler() throws Exception {
        RawServiceSpec rawServiceSpec = getRawServiceSpec("svc.yml");
        SchedulerFlags schedulerFlags = SchedulerFlags.fromMap(envVars);
        SparkService sparkScheduler = new SparkService(rawServiceSpec, schedulerFlags);
        Assert.assertNotNull(sparkScheduler);
    }

}
