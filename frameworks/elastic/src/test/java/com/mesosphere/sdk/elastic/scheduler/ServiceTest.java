package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.testing.*;
import org.junit.Test;

public class ServiceTest {
    private static final String HOST_RULE = "[[\"@hostname\", \"UNIQUE\"]]";
    private static final String MAX_PER_ZONE_RULE = "[[\"@zone\", \"MAX_PER\", \"3\"]]";
    private static final String GROUP_BY_ZONE_RULE = "[[\"@zone\", \"GROUP_BY\", \"3\"]]";
    private static final ConfigValidator<ServiceSpec> validator = new ElasticZoneValidator();

    @Test
    public void testSpec() throws Exception {
        getDefaultRunner().run();
    }

    @Test
    public void rejectRackEnablement() throws Exception {
        ConfigValidatorUtils.rejectRackEnablement(validator, getDefaultRunner(), "MASTER_NODE_PLACEMENT");
        ConfigValidatorUtils.rejectRackEnablement(validator, getDefaultRunner(), "DATA_NODE_PLACEMENT");
        ConfigValidatorUtils.rejectRackEnablement(validator, getDefaultRunner(), "INGEST_NODE_PLACEMENT");
        ConfigValidatorUtils.rejectRackEnablement(validator, getDefaultRunner(), "COORDINATOR_NODE_PLACEMENT");
    }

    @Test
    public void rejectRackDisablement() throws Exception {
        ConfigValidatorUtils.rejectRackDisablement(validator, getDefaultRunner(), "MASTER_NODE_PLACEMENT");
        ConfigValidatorUtils.rejectRackDisablement(validator, getDefaultRunner(), "DATA_NODE_PLACEMENT");
        ConfigValidatorUtils.rejectRackDisablement(validator, getDefaultRunner(), "INGEST_NODE_PLACEMENT");
        ConfigValidatorUtils.rejectRackDisablement(validator, getDefaultRunner(), "COORDINATOR_NODE_PLACEMENT");
    }

    @Test
    public void allowRackChanges() throws Exception {
        ConfigValidatorUtils.allowRackChanges(validator, getDefaultRunner(), "MASTER_NODE_PLACEMENT");
        ConfigValidatorUtils.allowRackChanges(validator, getDefaultRunner(), "DATA_NODE_PLACEMENT");
        ConfigValidatorUtils.allowRackChanges(validator, getDefaultRunner(), "INGEST_NODE_PLACEMENT");
        ConfigValidatorUtils.allowRackChanges(validator, getDefaultRunner(), "COORDINATOR_NODE_PLACEMENT");
    }

    private ServiceTestRunner getDefaultRunner() {
        // `CLUSTER_NAME` and `CUSTOM_YAML_BLOCK` are set in our Main.java.
        // `ZONE` is set during offer evaluation.
        // `elastic-version`, `elastic-statsd-version` and `support-diagnostics-version` would normally be provided via
        // elastic's build.sh/versions.sh.
        return new ServiceTestRunner()
                .setPodEnv(
                        "master", "CLUSTER_NAME",
                        "cluster-foo", "CUSTOM_YAML_BLOCK",
                        "some.thing=true",
                        "ZONE", "us-east-1a")
                .setPodEnv(
                        "data", "CLUSTER_NAME",
                        "cluster-foo", "CUSTOM_YAML_BLOCK",
                        "some.thing=true",
                        "ZONE", "us-east-1a")
                .setPodEnv(
                        "ingest", "CLUSTER_NAME",
                        "cluster-foo", "CUSTOM_YAML_BLOCK",
                        "some.thing=true",
                        "ZONE", "us-east-1a")
                .setPodEnv(
                        "coordinator", "CLUSTER_NAME",
                        "cluster-foo", "CUSTOM_YAML_BLOCK",
                        "some.thing=true",
                        "ZONE", "us-east-1a")
                .setBuildTemplateParams(
                        "elastic-version", "6.1.3",
                        "elastic-statsd-version", "6.1.3.0", // NOPMD
                        "support-diagnostics-version", "6.3");
    }
}
