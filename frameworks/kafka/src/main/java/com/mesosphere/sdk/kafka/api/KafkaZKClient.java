package com.mesosphere.sdk.kafka.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.InvalidParameterException;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

/**
 * Read-only interface for retrieving information from ZooKeeper for Kafka brokers and topics.
 */
public class KafkaZKClient {
    private static final Logger log = LoggerFactory.getLogger(KafkaZKClient.class);

    private static final int POLL_DELAY_MS = 1000;
    private static final int CURATOR_MAX_RETRIES = 3;
    private static final String BROKER_ID_PATH = "/brokers/ids";
    private static final String TOPICS_PATH = "/brokers/topics";

    private final CuratorFramework zkClient;
    private final String kafkaZkUri;
    private final String kafkaServicePath;

    public String getZKString() {
        return  kafkaZkUri + kafkaServicePath;
    }

    public KafkaZKClient(String kafkaZkUri, String servicePath) {
        if (kafkaZkUri == null) {
            throw new InvalidParameterException("Zookeeper URI is not set. Can not start client.");
        }
        if (servicePath == null) {
            throw new InvalidParameterException("Service Path is not set. Can not start client.");
        }
        this.zkClient = CuratorFrameworkFactory.newClient(
                kafkaZkUri,
                new ExponentialBackoffRetry(POLL_DELAY_MS, CURATOR_MAX_RETRIES));
        this.zkClient.start();
        this.kafkaZkUri = kafkaZkUri;
        this.kafkaServicePath = servicePath;
        log.info("Zookeeper Client URI: {} servicePath: {}", kafkaZkUri, kafkaServicePath);
    }

    public JSONArray listBrokers() throws Exception {
        try {
            log.info("Zookeeper Client list brokers path: {}{}", kafkaServicePath,  BROKER_ID_PATH);
            return new JSONArray(zkClient.getChildren().forPath(kafkaServicePath + BROKER_ID_PATH));
        } catch (KeeperException.NoNodeException e) {
            log.info("List path: " + kafkaServicePath + BROKER_ID_PATH
                    + " doesn't exist, returning empty brokers list. Kafka not running yet?", e);
            return new JSONArray();
        }
    }

    public Optional<JSONObject> getBroker(String id) throws Exception {
        log.info("Zookeeper Client get broker path: {}{}/{} ", kafkaServicePath, BROKER_ID_PATH, id);
        List<String> ids = zkClient.getChildren().forPath(kafkaServicePath + BROKER_ID_PATH);
        if (!ids.contains(id)) {
            log.warn("Zookeeper Client broker id {} does not exists", id);
            return Optional.empty();
        }
        return Optional.of(
                new JSONObject(
                        new String(zkClient.getData().
                               forPath(kafkaServicePath + BROKER_ID_PATH
                                       + "/" + id), "UTF-8")));
    }

    public JSONArray listTopics() throws Exception {
        try {
            log.info("Zookeeper Client list topics path: " + kafkaServicePath + BROKER_ID_PATH);
            return new JSONArray(zkClient.getChildren().forPath(kafkaServicePath + TOPICS_PATH));
        } catch (KeeperException.NoNodeException e) {
            log.info("List path: " + kafkaServicePath + TOPICS_PATH
                    + " doesn't exist, returning empty topics list. Kafka not running yet?", e);
            return new JSONArray();
        }
    }

    public JSONObject getTopic(String topicName) throws Exception {
        List<String> partitionIdList = zkClient.getChildren()
                .forPath(kafkaServicePath + TOPICS_PATH + "/" + topicName + "/partitions");
        List<JSONObject> partitions = new ArrayList<JSONObject>();
        for (String partitionId : partitionIdList) {
            JSONObject state = new JSONObject(
                                    new String(zkClient.getData().forPath (
                                            kafkaServicePath + TOPICS_PATH + "/"
                                                    + topicName + "/partitions" + "/" + partitionId + "/state"),
                                            "UTF-8"));
            partitions.add((new JSONObject()).put(partitionId, state));
        }
        return (new JSONObject()).put("partitions", partitions);
    }

    public List<String> getBrokerEndpoints() {
        List<String> endpoints = new ArrayList<>();
        try {
            List<String> ids = zkClient.getChildren().forPath(kafkaServicePath + BROKER_ID_PATH);
            for (String id : ids) {
                byte[] bytes = zkClient.getData().forPath(kafkaServicePath + BROKER_ID_PATH + "/" + id);
                JSONObject broker = new JSONObject(new String(bytes, "UTF-8"));
                String host = (String) broker.get("host");
                Integer port = (Integer) broker.get("port");
                endpoints.add(host + ":" + port);
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve broker endpoints with exception: ", ex);
        }
        return endpoints;
    }
}
