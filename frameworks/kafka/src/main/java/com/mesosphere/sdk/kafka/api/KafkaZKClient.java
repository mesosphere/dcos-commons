package com.mesosphere.sdk.kafka.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Optional;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Read-only interface for retrieving information from ZooKeeper for Kafka brokers and topics.
 */
public class KafkaZKClient {
    private static final Logger log = LoggerFactory.getLogger(KafkaZKClient.class);

    private static final int POLL_DELAY_MS = 1000;
    private static final int CURATOR_MAX_RETRIES = 3;
    private static final String IDS_PATH = "/brokers/ids";
    private static final String TOPICS_PATH = "/brokers/topics";

    private static final String PROTOCOL_NAME_PLAINTEXT = "PLAINTEXT";
    private static final String PROTOCOL_NAME_TLS = "SSL";

    private final CuratorFramework zkClient;

    /**
     * @param kafkaConnectString the ZK URI being used by kafka, of the form "host:port/path/to/node",
     *      e.g. "mesos.master:2181/dcos-service-path__to__kafka"
     */
    public KafkaZKClient(String kafkaConnectString) {
        log.info("Zookeeper Client URI: {}", kafkaConnectString);
        this.zkClient = CuratorFrameworkFactory.builder()
                .connectString(kafkaConnectString)
                .retryPolicy(new ExponentialBackoffRetry(POLL_DELAY_MS, CURATOR_MAX_RETRIES))
                .build();
        this.zkClient.start();
    }

    public JSONArray listBrokers() throws Exception {
        try {
            return new JSONArray(zkClient.getChildren().forPath(IDS_PATH));
        } catch (KeeperException.NoNodeException e) {
            log.info("List path: " + IDS_PATH
                    + " doesn't exist, returning empty brokers list. Kafka not running yet?", e);
            return new JSONArray();
        }
    }

    public Optional<JSONObject> getBroker(String id) throws Exception {
        List<String> ids = zkClient.getChildren().forPath(IDS_PATH);
        if (!ids.contains(id)) {
            return Optional.empty();
        }
        return Optional.of(new JSONObject(new String(
                zkClient.getData().forPath(IDS_PATH + "/" + id), StandardCharsets.UTF_8)));
    }

    public JSONArray listTopics() throws Exception {
        try {
            return new JSONArray(zkClient.getChildren().forPath(TOPICS_PATH));
        } catch (KeeperException.NoNodeException e) {
            log.info("List path: " + TOPICS_PATH
                    + " doesn't exist, returning empty topics list. Kafka not running yet?", e);
            return new JSONArray();
        }
    }

    public JSONObject getTopic(String topicName) throws Exception {
        String partitionsNode = TOPICS_PATH + "/" + topicName + "/partitions";
        List<String> partitionIdList = zkClient.getChildren().forPath(partitionsNode);
        List<JSONObject> partitions = new ArrayList<JSONObject>();
        for (String partitionId : partitionIdList) {
            JSONObject state = new JSONObject(new String(
                    zkClient.getData().forPath(partitionsNode + "/" + partitionId + "/state"),
                    StandardCharsets.UTF_8));
            partitions.add((new JSONObject()).put(partitionId, state));
        }
        return (new JSONObject()).put("partitions", partitions);
    }

    public List<String> getBrokerEndpoints() {
        try {
            return getBrokerEndpoints(PROTOCOL_NAME_PLAINTEXT);
        } catch (Exception ex) {
            log.error("Failed to retrieve broker endpoints with exception: ", ex);
        }
        return Collections.emptyList();
    }

    public List<String> getBrokerTLSEndpoints() {
        try {
            return getBrokerEndpoints(PROTOCOL_NAME_TLS);
        } catch (Exception ex) {
            log.error("Failed to retrieve broker TLS endpoints with exception: ", ex);
        }
        return Collections.emptyList();
    }

    private List<String> getBrokerEndpoints(final String protocolName) throws Exception {
        final List<String> endpoints = new ArrayList<>();

        final List<String> ids = zkClient.getChildren().forPath(IDS_PATH);
        for (String id : ids) {
            byte[] bytes = zkClient.getData().forPath(IDS_PATH + "/" + id);
            JSONObject broker = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            final String mappedProtocolName = broker
                    .getJSONObject("listener_security_protocol_map")
                    .getString(protocolName);
            endpoints.addAll(
                    StreamSupport.stream(broker.getJSONArray("endpoints").spliterator(), false)
                        .map(endpoint -> endpoint.toString())
                        .filter(endpoint -> endpoint.startsWith(mappedProtocolName + "://"))
                        .map(endpoint -> endpoint.substring((mappedProtocolName + "://").length()))
                        .collect(Collectors.toList())
            );
        }

        return endpoints;
    }
}
