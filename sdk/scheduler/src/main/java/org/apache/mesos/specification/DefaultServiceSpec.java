package org.apache.mesos.specification;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.mesos.config.ConfigurationComparator;

import java.util.List;

/**
 * Default implementation of {@link ServiceSpec}.
 */
public class DefaultServiceSpec implements ServiceSpec {
    private static final Comparator COMPARATOR = new Comparator();

    private String name;
    private String role;
    private String principal;
    private int apiPort;
    private String zookeeperConnection;
    private List<PodSpec> pods;

    DefaultServiceSpec(String name,
                       String role,
                       String principal,
                       int apiPort,
                       String zookeeperConnection,
                       List<PodSpec> pods) {
        this.name = name;
        this.role = role;
        this.principal = principal;
        this.apiPort = apiPort;
        this.zookeeperConnection = zookeeperConnection;
        this.pods = pods;
    }

    @Override
    public String getName() {
        return name;
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
    public int getApiPort() {
        return apiPort;
    }

    @Override
    public String getZookeeperConnection() {
        return zookeeperConnection;
    }

    @Override
    public List<PodSpec> getPods() {
        return pods;
    }

    /**
     * Returns a {@link ConfigurationComparer} which may be used to compare
     * {@link DefaultServiceSpecification}s.
     */
    public static ConfigurationComparator<ServiceSpec> getComparatorInstance() {
        return COMPARATOR;
    }

    /**
     * Comparer which checks for equality of {@link DefaultServiceSpecification}s.
     */
    public static class Comparator implements ConfigurationComparator<ServiceSpec> {

        /**
         * Call {@link DefaultServiceSpecification#getComparatorInstance()} instead.
         */
        private Comparator() { }

        @Override
        public boolean equals(ServiceSpec first, ServiceSpec second) {
            return EqualsBuilder.reflectionEquals(first, second);
        }
    }

    public static class Builder {
        private String name;
        private String role;
        private String principal;
        private int apiPort;
        private String zookeeperConnection;
        private List<PodSpec> pods;

        public static Builder newBuilder() {
            return new Builder();
        }
        public static Builder newBuilder(DefaultServiceSpec spec) {
            return new Builder();
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder principal(String principal) {
            this.principal = principal;
            return this;
        }

        public Builder apiPort(int apiPort) {
            this.apiPort = apiPort;
            return this;
        }

        public Builder zookeeperConnection(String zookeeperConnection) {
            this.zookeeperConnection = zookeeperConnection;
            return this;
        }

        public Builder pods(List<PodSpec> pods) {
            this.pods = pods;
            return this;
        }

        public DefaultServiceSpec build() {
            return new DefaultServiceSpec(name, role, principal, apiPort, zookeeperConnection, pods);
        }
    }
}
