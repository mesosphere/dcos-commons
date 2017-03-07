package com.mesosphere.sdk.kafka.api;

import com.mesosphere.sdk.api.types.EndpointProducer;

/**
 * Endpoint Resource.
 */
public class BrokerAddress implements EndpointProducer {
    private final KafkaZKClient zkClient;

    public BrokerAddress(KafkaZKClient zkClient) {
        this.zkClient = zkClient;
    }
    public String getEndpoint() {
        return zkClient.getBrokerEndpoints().toString();
    }
}
