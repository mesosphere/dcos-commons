package com.mesosphere.sdk.kafka.api;

import com.mesosphere.dcos.kafka.scheduler.KafkaScheduler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Path("/v1/brokers")
@Produces("application/json")
public class BrokerController {
  private final Log log = LogFactory.getLog(BrokerController.class);
  private final KafkaScheduler kafkaScheduler;

  public BrokerController(KafkaScheduler kafkaScheduler) {
    this.kafkaScheduler = kafkaScheduler;
  }

  @GET
  public Response listBrokers() {
    try {
      return Response.ok(kafkaScheduler.getKafkaState().getBrokerIds(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch broker ids", ex);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/{id}")
  public Response getBroker(@PathParam("id") String id) {
    try {
      Optional<JSONObject> brokerObj = kafkaScheduler.getKafkaState().getBroker(id);
      if (brokerObj.isPresent()) {
        return Response.ok(brokerObj, MediaType.APPLICATION_JSON).build();
      } else {
        return Response.status(Response.Status.NOT_FOUND).build();
      }
    } catch (Exception ex) {
      log.error("Failed to fetch broker id: " + id, ex);
      return Response.serverError().build();
    }
  }

  @PUT
  @Path("/{id}")
  public Response killBrokers(
    @PathParam("id") String id,
    @QueryParam("replace") String replace) {

    try {
      int idVal = Integer.parseInt(id);
      Optional<Protos.TaskInfo> taskInfoOptional = kafkaScheduler.getFrameworkState().getTaskInfoForBroker(idVal);
      if (!taskInfoOptional.isPresent()) {
        // Tests expect an array containing a single null element in this case. May make sense to
        // revisit this strange behavior someday...
        log.error(String.format(
            "Broker %d doesn't exist in FrameworkState, returning null entry in response", idVal));
        return killResponse(Arrays.asList((String)null));
      }
      return killBroker(taskInfoOptional.get(), Boolean.parseBoolean(replace));
    } catch (Exception ex) {
      log.error("Failed to kill brokers", ex);
      return Response.serverError().build();
    }
  }

  private Response killBroker(Protos.TaskInfo taskInfo, boolean replace) {
    try {
      if (replace) {
        KafkaScheduler.rescheduleTask(taskInfo);
      } else {
        KafkaScheduler.restartTasks(taskInfo);
      }
      return killResponse(Arrays.asList(taskInfo.getTaskId().getValue()));
    } catch (Exception ex) {
      log.error("Failed to kill brokers", ex);
      return Response.serverError().build();
    }
  }

  private static Response killResponse(List<String> taskIds) {
    return Response.ok(new JSONArray(taskIds).toString(), MediaType.APPLICATION_JSON).build();
  }
}
