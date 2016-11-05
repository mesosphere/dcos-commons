package com.mesosphere.sdk.elastic.scheduler;

import com.google.common.base.Joiner;
import org.apache.mesos.specification.DefaultConfigFileSpecification;

class KibanaConfigurator {
    private DefaultConfigFileSpecification kibanaConfigFileSpecification;

    KibanaConfigurator(String relativePath) {
        kibanaConfigFileSpecification = new DefaultConfigFileSpecification(relativePath, templateContent());
    }

    DefaultConfigFileSpecification getKibanaConfigFileSpecification() {
        return kibanaConfigFileSpecification;
    }

    private String templateContent() {
        String[] entries = {
            "elasticsearch.url: {{KIBANA_ELASTICSEARCH_URL}}",
            "elasticsearch.username: kibana",
            "elasticsearch.password: {{KIBANA_PASSWORD}}",
            "server.name: {{KIBANA_SERVER_NAME}}",
            "server.host: 0.0.0.0",
            "server.port: {{KIBANA_PORT}}",
            "xpack.security.encryptionKey: {{KIBANA_ENCRYPTION_KEY}}",
            "xpack.reporting.encryptionKey: {{KIBANA_ENCRYPTION_KEY}}"};
        return Joiner.on("\n").join(entries);
    }
}
