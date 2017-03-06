package com.mesosphere.sdk.kafka.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

import com.mesosphere.sdk.api.ResponseUtils;

import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.util.Optional;

/**
 *  Broker Resource.
 */
@Path("/v1/brokers")
@Produces("application/json")
public class BrokerResource {
    private final Log log = LogFactory.getLog(BrokerResource.class);
    private KafkaZKClient kafkaZkClient;

    public BrokerResource(KafkaZKClient kafkaZkClient) {
        this.kafkaZkClient = kafkaZkClient;
    }

    @GET
    public Response listBrokers() {
        try {
            return ResponseUtils.jsonOkResponse(kafkaZkClient.listBrokers());
        } catch (Exception ex) {
            log.error("Failed to fetch broker ids with exception:", ex);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/{id}")
    public Response getBroker(@PathParam("id") String id) {
        try {
            Optional<JSONObject> brokerObj = kafkaZkClient.getBroker(id);
            if (brokerObj.isPresent()) {
                return ResponseUtils.jsonOkResponse(brokerObj.get());
            }
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (Exception ex) {
            log.error("Failed to fetch broker id with exception: " + id, ex);
            return Response.serverError().build();
        }
    }
}
