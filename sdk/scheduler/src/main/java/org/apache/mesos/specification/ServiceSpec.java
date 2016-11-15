package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.mesos.config.Configuration;

import java.util.List;

/**
 * Defines a {@link Service}.
 */
public interface ServiceSpec extends Configuration {
    @JsonProperty("name")
    String getName();

    @JsonProperty("role")
    String getRole();

    @JsonProperty("principal")
    String getPrincipal();

    @JsonProperty("pod_specs")
    List<PodSpec> getPods();

    @JsonProperty("api_port")
    int getApiPort();

    @JsonProperty("zookeeper")
    String getZookeeperConnection();

    @JsonProperty("replacement_failure_policy")
    ReplacementFailurePolicy getReplacementFailurePolicy();
}
