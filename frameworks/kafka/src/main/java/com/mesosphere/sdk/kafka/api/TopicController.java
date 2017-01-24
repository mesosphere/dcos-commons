package com.mesosphere.sdk.kafka.api;

import com.mesosphere.dcos.kafka.cmd.CmdExecutor;
import com.mesosphere.dcos.kafka.scheduler.KafkaScheduler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;

@Path("/v1/topics")
public class TopicController {
  private static final Log log = LogFactory.getLog(TopicController.class);

  private final CmdExecutor cmdExecutor;
  private final KafkaScheduler kafkaScheduler;

  public TopicController(CmdExecutor cmdExecutor, KafkaScheduler kafkaScheduler) {
    this.cmdExecutor = cmdExecutor;
    this.kafkaScheduler = kafkaScheduler;
  }

  @GET
  public Response topics() {
    try {
      JSONArray topics = kafkaScheduler.getKafkaState().getTopics();
      return Response.ok(topics.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch topics with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @POST
  public Response createTopic(
      @QueryParam("name") String name,
      @QueryParam("partitions") String partitionCount,
      @QueryParam("replication") String replicationFactor) {

    try {
      int partCount = Integer.parseInt(partitionCount);
      int replFactor = Integer.parseInt(replicationFactor);
      JSONObject result = cmdExecutor.createTopic(name, partCount, replFactor);
      return Response.ok(result.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to create topic: " + name + " with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/unavailable_partitions")
  public Response unavailablePartitions() {
    try {
      JSONObject obj = cmdExecutor.unavailablePartitions();
      return Response.ok(obj.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch topics with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/under_replicated_partitions")
  public Response underReplicatedPartitions() {
    try {
      JSONObject obj = cmdExecutor.underReplicatedPartitions();
      return Response.ok(obj.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch topics with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/{name}")
  public Response getTopic(@PathParam("name") String topicName) {
    try {
      JSONObject topic = kafkaScheduler.getKafkaState().getTopic(topicName);
      return Response.ok(topic.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch topic: " + topicName + " with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @PUT
  @Path("/{name}")
  public Response operationOnTopic(
      @PathParam("name") String name,
      @QueryParam("operation") String operation,
      @QueryParam("key") String key,
      @QueryParam("value") String value,
      @QueryParam("partitions") String partitions,
      @QueryParam("messages") String messages) {

    try {
      JSONObject result = null;
      List<String> cmds = null;

      if (operation == null) {
        result = new JSONObject();
        result.put("Error", "Must designate an 'operation'.  Possibles operations are [producer-test, delete, partitions, config, deleteConfig].");
      } else {
        switch (operation) {
          case "producer-test":
            int messageCount = Integer.parseInt(messages);
            result = cmdExecutor.producerTest(name, messageCount);
            break;
          case "partitions":
            cmds = Arrays.asList("--partitions", partitions);
            result = cmdExecutor.alterTopic(name, cmds);
            break;
          default:
            result = new JSONObject();
            result.put("Error", "Unrecognized operation: " + operation);
            break;
        }
      }

      return Response.ok(result.toString(), MediaType.APPLICATION_JSON).build();

    } catch (Exception ex) {
      log.error("Failed to perform operation: " + operation + " on Topic: " + name + " with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @DELETE
  @Path("/{name}")
  public Response deleteTopic(
      @PathParam("name") String name) {

    try {
      JSONObject result = cmdExecutor.deleteTopic(name);
      String message = result.getString("message");
      if (message.contains("This will have no impact if delete.topic.enable is not set to true")) {
        return Response.accepted().entity(result.toString()).type(MediaType.APPLICATION_JSON).build();
      } else {
        return Response.ok(result.toString(), MediaType.APPLICATION_JSON).build();
      }

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
      return Response.ok(offsets.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch offsets for: " + topicName + " with exception: " + ex);
      return Response.serverError().build();
    }
  }
}
