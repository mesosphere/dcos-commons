package com.mesosphere.sdk.kafka.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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

    private final String kafkaZkUri;
    private final String brokerIdPath;

    public BrokerController(String kafkaZkUri) {
        this.kafkaZkUri = kafkaZkUri;

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
}
