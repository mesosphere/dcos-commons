package com.mesosphere.sdk.kafka.cmd;

import com.mesosphere.sdk.kafka.api.KafkaZKClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * A simple command executor class. Migrated from dcos-kafka-service.
 */
public class CmdExecutor {
    private static final Log log = LogFactory.getLog(CmdExecutor.class);

    private final String binPath;
    private final String zkUri;
    private final KafkaZKClient kafkaZkClient;

    public CmdExecutor(KafkaZKClient kafkaZkClient, String kafkaZookeeperUri, String kafkaSandboxPath) {
        this.binPath = kafkaSandboxPath + "/bin/";
        this.kafkaZkClient = kafkaZkClient;
        this.zkUri = kafkaZookeeperUri;
    }

    public JSONObject createTopic(String name, int partitionCount, int replicationFactor) throws Exception {
        /*
         e.g. ./kafka-topics.sh --create --zookeeper master.mesos:2181/kafka-0 --topic topic0 --partitions 3
          --replication-factor 3
         */
        List<String> cmd = new ArrayList<String>();
        cmd.add(binPath + "kafka-topics.sh");
        cmd.add("--create");
        cmd.add("--zookeeper");
        cmd.add(zkUri);
        cmd.add("--topic");
        cmd.add(name);
        cmd.add("--partitions");
        cmd.add(Integer.toString(partitionCount));
        cmd.add("--replication-factor");
        cmd.add(Integer.toString(replicationFactor));

        return runCmd(cmd);
    }

    public JSONObject deleteTopic(String name) throws Exception {
        /*
         e.g. ./kafka-topics.sh --delete --zookeeper master.mesos:2181/kafka --topic topic0
         */
        List<String> cmd = new ArrayList<String>();
        cmd.add(binPath + "kafka-topics.sh");
        cmd.add("--delete");
        cmd.add("--zookeeper");
        cmd.add(zkUri);
        cmd.add("--topic");
        cmd.add(name);

        return runCmd(cmd);
    }

    public JSONObject alterTopic(String name, List<String> cmds) throws Exception {
        /*
        e.g. ./kafka-topics.sh --zookeeper master.mesos:2181/kafka --alter --topic topic0 --partitions 4
        */
        List<String> cmd = new ArrayList<String>();
        cmd.add(binPath + "kafka-topics.sh");
        cmd.add("--alter");
        cmd.add("--zookeeper");
        cmd.add(zkUri);
        cmd.add("--topic");
        cmd.add(name);
        cmd.addAll(cmds);

        return runCmd(cmd);
    }

    public JSONObject producerTest(String topicName, int messages) throws Exception {
        /* e.g. ./kafka-producer-perf-test.sh --topic topic0 --num-records 1000 --producer-props
         bootstrap.servers=
         ip-10-0-2-171.us-west-2.compute.internal:9092,ip-10-0-2-172.us-west-2.compute.internal:9093,
         ip-10-0-2-173.us-west-2.compute.internal:9094 --throughput 100000 --record-size 1024
         */
        List<String> brokerEndpoints = kafkaZkClient.getBrokerEndpoints();
        if (brokerEndpoints.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "No brokers were found to run producer test against topic %s", topicName));
        }

        List<String> cmd = getProducerPerfCommand(topicName, messages, brokerEndpoints);

        return runCmd(cmd);
    }

    public JSONObject producerTestOverTLS(String topicName, int messages) throws Exception {
        /* e.g. ./kafka-producer-perf-test.sh --topic topic0 --num-records 1000 --producer-props
         bootstrap.servers=
         ip-10-0-2-171.us-west-2.compute.internal:9092,ip-10-0-2-172.us-west-2.compute.internal:9093,
         ip-10-0-2-173.us-west-2.compute.internal:9094 security.protocol=SSL --throughput 100000 --record-size 1024
         */
        List<String> brokerEndpoints = kafkaZkClient.getBrokerTLSEndpoints();
        if (brokerEndpoints.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "No brokers were found to run producer test against topic %s", topicName));
        }

        List<String> cmd = getProducerPerfCommand(topicName, messages, brokerEndpoints);
        // Configure TLS protocol
        cmd.add("security.protocol=SSL");

        return runCmd(cmd);
    }

    private List<String> getProducerPerfCommand(String topicName, int messages, List<String> endpoints) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(binPath + "kafka-producer-perf-test.sh");
        cmd.add("--topic");
        cmd.add(topicName);
        cmd.add("--num-records");
        cmd.add(Integer.toString(messages));
        cmd.add("--throughput");
        cmd.add("100000");
        cmd.add("--record-size");
        cmd.add("1024");
        cmd.add("--producer-props");
        cmd.add("bootstrap.servers=" + StringUtils.join(endpoints, ","));
        return cmd;
    }

    public JSONArray getOffsets(String topicName, Long time) throws Exception {
        /*
         e.g. ./kafka-run-class.sh kafka.tools.GetOffsetShell
         --broker-list ip-10-0-1-71.us-west-2.compute.internal:9092,
           ip-10-0-1-72.us-west-2.compute.internal:9093,ip-10-0-1-68.us-west-2.compute.internal:9094
           --topic topic0 --time -1 --partitions 0
        */
        List<String> brokerEndpoints = kafkaZkClient.getBrokerEndpoints();
        if (brokerEndpoints.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "No brokers were found to get offsets for topic %s", topicName));
        }

        List<String> cmd = new ArrayList<String>();
        cmd.add(binPath + "kafka-run-class.sh");
        cmd.add("kafka.tools.GetOffsetShell");
        cmd.add("--topic");
        cmd.add(topicName);
        cmd.add("--time");
        cmd.add(String.valueOf(time));
        cmd.add("--broker-list");
        cmd.add(StringUtils.join(brokerEndpoints, ","));

        String stdout = (String) runCmd(cmd).get("message");
        stdout = stdout.substring("Output: ".length());
        return getPartitions(stdout);
    }

    public JSONObject unavailablePartitions() throws Exception {
        // e.g. ./kafka-topics.sh --zookeeper master.mesos:2181/kafka --describe --unavailable-partitions

        List<String> cmd = new ArrayList<String>();
        cmd.add(binPath + "kafka-topics.sh");
        cmd.add("--describe");
        cmd.add("--zookeeper");
        cmd.add(zkUri);
        cmd.add("--unavailable-partitions");

        return runCmd(cmd);
    }

    public JSONObject underReplicatedPartitions() throws Exception {
        // e.g. ./kafka-topics.sh --zookeeper master.mesos:2181/kafka --describe --under-replicate-partitions

        List<String> cmd = new ArrayList<String>();
        cmd.add(binPath + "kafka-topics.sh");
        cmd.add("--describe");
        cmd.add("--zookeeper");
        cmd.add(zkUri);
        cmd.add("--under-replicated-partitions");

        return runCmd(cmd);
    }

    private static JSONArray getPartitions(String offsets) {
        List<JSONObject> partitions = new ArrayList<JSONObject>();

        String lines[] = offsets.split("\\n");
        for (String line : lines) {
            // e.g. topic0:2:33334
            String elements[] = line.trim().split(":");
            String partition = elements[1];

            String offset = "";
            if (elements.length > 2) {
                offset = elements[2];
            }

            JSONObject part = new JSONObject();
            part.put(partition, offset);
            partitions.add(part);
        }

        return new JSONArray(partitions);
    }

    private static JSONObject runCmd(List<String> cmd) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(cmd);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Process process = builder.start();
        int exitCode = process.waitFor();
        stopWatch.stop();

        String stdout = streamToString(process.getInputStream());
        String stderr = streamToString(process.getErrorStream());
        log.warn(String.format("stdout:%n%s", stdout));
        log.warn(String.format("stderr:%n%s", stderr));
        String message = createOutputMessage(stdout, stderr);

        if (exitCode == 0) {
            log.info(String.format(
                    "Command succeeded in %dms: %s",
                    stopWatch.getTime(), StringUtils.join(cmd, " ")));
        } else {
            log.warn(String.format(
                    "Command failed with code=%d in %dms: %s",
                    exitCode, stopWatch.getTime(), StringUtils.join(cmd, " ")));
            log.warn(String.format("stdout:%n%s", stdout));
            log.warn(String.format("stderr:%n%s", stderr));
        }

        JSONObject obj = new JSONObject();
        obj.put("message", message);

        return obj;
    }

    private static String createOutputMessage(String stdout, String stderr) {
        String message = "";
        if (StringUtils.isNotBlank(stdout)) {
            message += String.format("Output: %s", stdout);

            // error only if we have stdout
            if (StringUtils.isNotBlank(stderr)) {
                message += String.format(" Error: %s", stderr);
            }
        }
        return message;
    }

    private static String streamToString(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()));
        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
            builder.append(System.getProperty("line.separator"));
        }

        return builder.toString();
    }
}
