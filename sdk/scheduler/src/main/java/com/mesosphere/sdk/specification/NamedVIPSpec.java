package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.evaluate.NamedVIPEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * This class represents a port mapped to a DC/OS named VIP.
 */
public class NamedVIPSpec extends DefaultResourceSpec implements ResourceSpec {
    @NotNull
    @Size(min = 1)
    private final String portName;
    @NotNull
    @Size(min = 1)
    private final String protocol;
    @NotNull
    private final DiscoveryInfo.Visibility visibility;
    @NotNull
    @Size(min = 1)
    private final String vipName;
    @NotNull
    private final Integer vipPort;

    @JsonCreator
    public NamedVIPSpec(
            @JsonProperty("name") String name,
            @JsonProperty("value") Protos.Value value,
            @JsonProperty("role") String role,
            @JsonProperty("principal") String principal,
            @JsonProperty("env-key") String envKey,
            @JsonProperty("port-name") String portName,
            @JsonProperty("protocol") String protocol,
            @JsonProperty("visibility") DiscoveryInfo.Visibility visibility,
            @JsonProperty("vip-name") String vipName,
            @JsonProperty("vip-port") Integer vipPort) {
        super(name, value, role, principal, envKey);
        this.portName = portName;
        this.protocol = protocol;
        this.visibility = visibility;
        this.vipName = vipName;
        this.vipPort = vipPort;

        ValidationUtils.validate(this);
    }

    @JsonProperty("port-name")
    public String getPortName() {
        return portName;
    }

    @JsonProperty("protocol")
    public String getProtocol() {
        return protocol;
    }

    @JsonProperty("visibility")
    public DiscoveryInfo.Visibility getVisibility() {
        return visibility;
    }

    @JsonProperty("vip-name")
    public String getVipName() {
        return vipName;
    }

    @JsonProperty("vip-port")
    public Integer getVipPort() {
        return vipPort;
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(Protos.Resource resource, String taskName) {
        return new NamedVIPEvaluationStage(
                resource,
                taskName,
                getPortName(),
                (int) getValue().getRanges().getRange(0).getBegin(),
                getProtocol(),
                getVisibility(),
                getVipName(),
                getVipPort());
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
