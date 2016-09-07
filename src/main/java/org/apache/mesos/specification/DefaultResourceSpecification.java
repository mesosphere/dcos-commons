package org.apache.mesos.specification;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

/**
 * This class provides a default implementation of the ResourceSpecification interface.
 */
public class DefaultResourceSpecification implements ResourceSpecification {
    private final String name;
    private final Protos.Value value;
    private final String role;
    private final String principal;

    public DefaultResourceSpecification(String name, Protos.Value value, String role, String principal) {
        this.name = name;
        this.value = value;
        this.role = role;
        this.principal = principal;
    }

    @Override
    public Protos.Value getValue() {
        return value;
    }

    @Override
    public String getRole() {
        return role;
    }

    @Override
    public String getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
