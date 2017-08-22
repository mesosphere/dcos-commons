package com.mesosphere.sdk.kafka.api;

import com.mesosphere.sdk.api.ResponseUtils;
import com.mesosphere.sdk.kafka.cmd.CmdExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import java.util.Arrays;
import java.util.List;

/**
 * Topic Resource executing commands through command executor.
 * Kafka package should also be deployed with framework to enable Kafka commands.
 */

@Path("/v1/topics")
public class TopicResource {
    private static final Logger log = LoggerFactory.getLogger(TopicResource.class);

    private final KafkaZKClient kafkaZkClient;
    private final CmdExecutor cmdExecutor;

    public TopicResource(CmdExecutor cmdExecutor, KafkaZKClient kafkaZkClient) {
        this.kafkaZkClient = kafkaZkClient;
        this.cmdExecutor = cmdExecutor;
    }

    @GET
    public Response topics() {
        try {
            return ResponseUtils.jsonOkResponse(kafkaZkClient.listTopics());
        } catch (Exception ex) {
            log.error("Failed to fetch topics with exception: " + ex);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/{name}")
    public Response getTopic(@PathParam("name") String topicName) {
        try {
            return ResponseUtils.jsonOkResponse(kafkaZkClient.getTopic(topicName));
        } catch (Exception ex) {
            log.error("Failed to fetch topic: " + topicName + " with exception: " + ex);
            return Response.serverError().build();
        }
    }

    @PUT
    @Path("/{name}")
    public Response createTopic(
            @PathParam("name") String name,
            @QueryParam("partitions") String partitionCount,
            @QueryParam("replication") String replicationFactor) {
        try {
            int partCount = Integer.parseInt(partitionCount);
            int replFactor = Integer.parseInt(replicationFactor);
            return ResponseUtils.jsonOkResponse(cmdExecutor.createTopic(name, partCount, replFactor));
        } catch (Exception ex) {
            log.error("Failed to create topic: " + name + " with exception: " + ex);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/unavailable_partitions")
    public Response unavailablePartitions() {
        try {
            return ResponseUtils.jsonOkResponse(cmdExecutor.unavailablePartitions());
        } catch (Exception ex) {
            log.error("Failed to fetch topics with exception: " + ex);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/under_replicated_partitions")
    public Response underReplicatedPartitions() {
        try {
            return ResponseUtils.jsonOkResponse(cmdExecutor.underReplicatedPartitions());
        } catch (Exception ex) {
            log.error("Failed to fetch topics with exception: " + ex);
            return Response.serverError().build();
        }
    }

    @PUT
    @Path("/{name}/operation/{type}")
    public Response operationOnTopic(
            @PathParam("name") String topicName,
            @PathParam("type") String type,
            @QueryParam("value") String value,
            @QueryParam("partitions") String partitions,
            @QueryParam("messages") String messages) {
        try {
            JSONObject result = null;
            List<String> cmds = null;
            if (type == null) {
                result = new JSONObject();
                result.put("Error", "Must designate an 'operation'.  " +
                        "Possibles operations are [producer-test, delete, partitions, config, deleteConfig].");
            } else {
                switch (type) {
                    case "producer-test": {
                        result = cmdExecutor.producerTest(topicName, Integer.parseInt(messages));
                        break;
                    }
                    case "producer-test-tls": {
                        result = cmdExecutor.producerTestOverTLS(topicName, Integer.parseInt(messages));
                        break;
                    }
                    case "partitions": {
                        cmds = Arrays.asList("--partitions", partitions);
                        result = cmdExecutor.alterTopic(topicName, cmds);
                        break;
                    }
                    default: {
                        result = new JSONObject();
                        result.put("Error", "Unrecognized operation: " + type);
                        break;
                    }
                }
            }
            return ResponseUtils.jsonOkResponse(result);
        } catch (Exception ex) {
            log.error("Failed to perform operation: " + type + " on Topic: " + topicName + " with exception: " + ex);
            return Response.serverError().build();
        }
    }

    @DELETE
    @Path("/{name}")
    public Response deleteTopic(@PathParam("name") String name) {
        try {
            JSONObject result = cmdExecutor.deleteTopic(name);
            String message = result.getString("message");
            Response.Status status;
            if (message.contains("This will have no impact if delete.topic.enable is not set to true")) {
                status = Response.Status.ACCEPTED;
            } else {
                status = Response.Status.OK;
            }
            return ResponseUtils.jsonResponse(result, status);
        } catch (Exception ex) {
            log.error("Failed to delete Topic: " + name + " with exception: " + ex);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/{name}/offsets")
    public Response getOffsets(@PathParam("name") String topicName, @QueryParam("time") Long time) {
        try {
            JSONArray offsets = cmdExecutor.getOffsets(topicName, time);
            return ResponseUtils.jsonOkResponse(offsets);
        } catch (Exception ex) {
            log.error("Failed to fetch offsets for: " + topicName + " with exception: " + ex);
            return Response.serverError().build();
        }
    }
}
