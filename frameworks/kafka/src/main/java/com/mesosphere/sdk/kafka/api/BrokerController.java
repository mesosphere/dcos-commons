package com.mesosphere.sdk.kafka.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;

/**
 *  Broker Controller.
 */
@Path("/v1/brokers")
@Produces("application/json")
public class BrokerController {
    private final Log log = LogFactory.getLog(BrokerController.class);
    private KafkaZKClient kafkaZkClient;

    public BrokerController(KafkaZKClient kafkaZkClient) {
        this.kafkaZkClient = kafkaZkClient;
    }

    @GET
    public Response listBrokers() {
        try {
            return Response.ok(kafkaZkClient.listBrokers(), MediaType.APPLICATION_JSON).build();
        } catch (Exception ex) {
            log.error("Failed to fetch broker ids", ex);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/{id}")
    public Response getBroker(@PathParam("id") String id) {
        try {
            Optional<JSONObject> brokerObj = kafkaZkClient.getBroker(id);
            if (brokerObj.isPresent()){
                return Response.ok(brokerObj.get(), MediaType.WILDCARD_TYPE.APPLICATION_JSON).build();
            }
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (Exception ex) {
            log.error("Failed to fetch broker id: " + id, ex);
            return Response.serverError().build();
        }
    }
}
