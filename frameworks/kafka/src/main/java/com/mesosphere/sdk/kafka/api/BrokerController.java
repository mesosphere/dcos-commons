package com.mesosphere.sdk.kafka.api;

import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.mesos.Protos;
import org.apache.zookeeper.KeeperException;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Optional;

/**
 *  Broker Controller
 */
@Path("/v1/brokers")
@Produces("application/json")
public class BrokerController {
    private static final int POLL_DELAY_MS = 1000;
    private static final int CURATOR_MAX_RETRIES = 3;
    private final CuratorFramework kafkaZkClient;
    private final Log log = LogFactory.getLog(BrokerController.class);

    private final StateStore stateStore;
    private final String kafkaZkUri;
    private final TaskKiller taskKiller;
    private final String brokerIdPath;

    public BrokerController(StateStore stateStore, TaskKiller taskKiller, String kafkaZkUri) {
        this.stateStore = stateStore;
        this.kafkaZkUri = kafkaZkUri;
        this.taskKiller = taskKiller;

        this.kafkaZkClient = CuratorFrameworkFactory.newClient(
                kafkaZkUri,
                new ExponentialBackoffRetry(POLL_DELAY_MS, CURATOR_MAX_RETRIES));
        this.kafkaZkClient.start();
        this.brokerIdPath = kafkaZkUri + "/brokers/ids";
    }


    @GET
    public Response listBrokers() {
        try {
            return Response.ok(new JSONArray(kafkaZkClient.getChildren().forPath(brokerIdPath)),
                    MediaType.APPLICATION_JSON).build();
        } catch (KeeperException.NoNodeException e) {
            log.info("List path: " + kafkaZkUri + " /brokers/ids"
                    + " doesn't exist, returning empty list. Kafka not running yet?", e);
            return Response.ok(new JSONArray(), MediaType.APPLICATION_JSON).build();
        } catch (Exception ex) {
            log.error("Failed to fetch broker ids", ex);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/{id}")
    public Response getBroker(@PathParam("id") String id) {
        try {
            if (!kafkaZkClient.getChildren().forPath(brokerIdPath).contains(id)) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(new JSONObject(new String(
                    kafkaZkClient.getData().forPath(brokerIdPath + "/" + id),
                    "UTF-8")), MediaType.WILDCARD_TYPE.APPLICATION_JSON).build();
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

        Optional<Protos.TaskInfo> taskInfoOptional;
        try {
            taskInfoOptional = stateStore.fetchTask("kafka-" + id + "-broker");
        } catch (StateStoreException e) {
            log.warn(String.format(
                    "Failed to get TaskInfo for broker " + id + ". This is expected when the service is "
                            + "starting for the first time."), e);
            taskInfoOptional = Optional.empty();
        }
        if (!taskInfoOptional.isPresent()) {
            log.error(String.format(
                    "Broker" + id + "doesn't exist in FrameworkState, returning null entry in response"));
            return Response.ok(new JSONArray(Arrays.asList((String) null)).toString(),
                    MediaType.APPLICATION_JSON).build();
        }
        try {
            if (Boolean.parseBoolean(replace)) {
                taskKiller.killTask(taskInfoOptional.get().getTaskId(), false);
            } else {
                taskKiller.killTask(taskInfoOptional.get().getTaskId(), true);
            }
            return Response.ok(new JSONArray((Arrays.asList(taskInfoOptional.get().getTaskId().getValue()))).toString(),
                    MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            log.error("Failed to kill brokers", e);
            return Response.serverError().build();
        }
    }
}
