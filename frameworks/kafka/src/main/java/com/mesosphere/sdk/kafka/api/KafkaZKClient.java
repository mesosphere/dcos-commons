package com.mesosphere.sdk.kafka.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
 * Read-only interface for retrieving information ZooKeeper for Kafka brokers and topics.
 */
public class KafkaZKClient {
    private static final Log log = LogFactory.getLog(KafkaZKClient.class);

    private static final int POLL_DELAY_MS = 1000;
    private static final int CURATOR_MAX_RETRIES = 3;
    private static final String BROKER_ID_PATH = "/brokers/ids";
    private static final String TOPICS_PATH = "/brokers/topics";

    private final CuratorFramework zkClient;
    private final String kafkaZkUri;

    public KafkaZKClient(String kafkaZkUri) {
        if (kafkaZkUri == null){
            throw new InvalidParameterException("KAFKA_VERSION_PATH is not set. Can not start CmdExecutor");
        }
        this.zkClient = CuratorFrameworkFactory.newClient(
                kafkaZkUri,
                new ExponentialBackoffRetry(POLL_DELAY_MS, CURATOR_MAX_RETRIES));
        this.zkClient.start();
        this.kafkaZkUri = kafkaZkUri;
    }

    public String getZKString(){
        return kafkaZkUri;
    }

    public JSONArray listBrokers() throws Exception {
        try {
            return new JSONArray(zkClient.getChildren().forPath(kafkaZkUri + BROKER_ID_PATH));
        } catch (KeeperException.NoNodeException e) {
            log.info("List path: " + kafkaZkUri + BROKER_ID_PATH
                    + " doesn't exist, returning empty list. Kafka not running yet?", e);
            return new JSONArray();
        }
    }

    public Optional<JSONObject> getBroker(String id) throws Exception {
        if (!zkClient.getChildren().forPath(kafkaZkUri + BROKER_ID_PATH).contains(id)) {
                return Optional.empty();
        }
        return Optional.of(
                new JSONObject(
                        new String(zkClient.getData().
                               forPath(kafkaZkUri + BROKER_ID_PATH + "/" + id), "UTF-8")));
    }

    public JSONArray listTopics() throws Exception {
        try {
            return new JSONArray(zkClient.getChildren().forPath(kafkaZkUri + TOPICS_PATH));
        } catch (KeeperException.NoNodeException e) {
            log.info("List path: " + kafkaZkUri + TOPICS_PATH
                    + " doesn't exist, returning empty list. Kafka not running yet?", e);
            return new JSONArray();
        }
    }

    public JSONObject getTopic(String topicName) throws Exception {
        List<String> partitionIdList = zkClient.getChildren()
                .forPath(kafkaZkUri + TOPICS_PATH + topicName + "/partitions");
        List<JSONObject> partitions = new ArrayList<JSONObject>();
        for (String partitionId : partitionIdList) {
            JSONObject state = new JSONObject(
                                    new String(zkClient.getData().forPath (
                                            kafkaZkUri + TOPICS_PATH
                                            + topicName + "/partitions" + "/" + partitionId + "/state"),
                                            "UTF-8"));
            partitions.add((new JSONObject()).put(partitionId, state));
        }
        return (new JSONObject()).put("partitions", partitions);
    }

    /* below is copied from kafka-broker-service */

    public List<String> getBrokerEndpoints() {
        List<String> endpoints = new ArrayList<String>();
        try {
            List<String> ids = zkClient.getChildren().forPath(kafkaZkUri + BROKER_ID_PATH);
            for (String id : ids) {
                byte[] bytes = zkClient.getData().forPath(kafkaZkUri + BROKER_ID_PATH + "/" + id);
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

    public List<String> getBrokerDNSEndpoints(String frameworkName) {
        List<String> endpoints = new ArrayList<String>();
        try {
            List<String> ids = zkClient.getChildren().forPath(kafkaZkUri + BROKER_ID_PATH);
            for (String id : ids) {
                byte[] bytes = zkClient.getData().forPath(kafkaZkUri + BROKER_ID_PATH + "/" + id);
                JSONObject broker = new JSONObject(new String(bytes, "UTF-8"));
                String host = "broker-" + id + "." + frameworkName + ".mesos";
                Integer port = (Integer) broker.get("port");
                endpoints.add(host + ":" + port);
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve broker DNS endpoints with exception: ", ex);
        }

        return endpoints;
    }

}
