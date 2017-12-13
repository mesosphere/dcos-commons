package com.mesosphere.sdk.elastic.scheduler;

import org.junit.Test;

import com.mesosphere.sdk.testing.ServiceTestRunner;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        // `CLUSTER_NAME` and `CUSTOM_YAML_BLOCK` are set in our Main.java.
        // `ZONE` is set during offer evaluation.
        // `elastic-version` and `support-diagnostics-version` would normally be provided via elastic's
        // build.sh/versions.sh.
        new ServiceTestRunner()
                .setPodEnv("master", "CLUSTER_NAME", "cluster-foo", "CUSTOM_YAML_BLOCK", "some.thing=true",
                           "ZONE", "us-east-1a")
                .setPodEnv("data", "CLUSTER_NAME", "cluster-foo", "CUSTOM_YAML_BLOCK", "some.thing=true",
                           "ZONE", "us-east-1a")
                .setPodEnv("ingest", "CLUSTER_NAME", "cluster-foo", "CUSTOM_YAML_BLOCK", "some.thing=true",
                           "ZONE", "us-east-1a")
                .setPodEnv("coordinator", "CLUSTER_NAME", "cluster-foo", "CUSTOM_YAML_BLOCK", "some.thing=true",
                           "ZONE", "us-east-1a")
                .setBuildTemplateParams("elastic-version", "1.2.3", "support-diagnostics-version", "4.5.6")
                .run();
    }
}
