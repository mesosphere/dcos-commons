package com.mesosphere.sdk.api;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.protobuf.Message;

/**
 * Utilities for building RPC responses.
 */
public class ResponseUtils {

    private ResponseUtils() {
        // do not instantiate
    }

    /**
     * Returns a 200 OK response containing the provided {@link JSONArray}.
     */
    public static Response jsonOkResponse(JSONArray jsonArray) {
        return jsonResponse(jsonArray, Response.Status.OK);
    }

    /**
     * Returns a response containing the provided {@link JSONArray}.
     */
    public static Response jsonResponse(JSONArray jsonArray, Response.Status status) {
        return jsonResponseBean(jsonArray.toString(2), status);
    }

    /**
     * Returns a 200 OK response containing the provided {@link JSONObject}.
     */
    public static Response jsonOkResponse(JSONObject jsonObject) {
        return jsonResponse(jsonObject, Response.Status.OK);
    }

    /**
     * Returns a 200 OK response containing the provided {@link JSONObject}.
     */
    public static Response jsonResponse(JSONObject jsonObject, Response.Status status) {
        return jsonResponseBean(jsonObject.toString(2), status);
    }

    /**
     * Returns a 200 OK response containing a JSON representation of the provided protobuf {@link Message}.
     */
    public static Response jsonOkResponse(Message protoMessage) {
        return jsonResponse(protoMessage, Response.Status.OK);
    }

    /**
     * Returns a 200 OK response containing a JSON representation of the provided protobuf {@link Message}.
     */
    public static Response jsonResponse(Message protoMessage, Response.Status status) {
        return jsonResponseBean(protoMessage, status);
    }

    /**
     * Returns a 200 OK response containing the provided JSON {@link String}.
     *
     * Resources which call this directly should extend {@link PrettyJsonResource}.
     */
    public static Response jsonResponseBean(Object entity, Response.Status status) {
        return Response.status(status).entity(entity).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    /**
     * Returns a 200 OK response containing the provided plaintext {@link String}.
     */
    public static Response plainOkResponse(String string) {
        return plainResponse(string, Response.Status.OK);
    }

    /**
     * Returns a 200 OK response containing the provided plaintext {@link String}.
     */
    public static Response plainResponse(String string, Response.Status status) {
        return Response.status(status).entity(string).type(MediaType.TEXT_PLAIN_TYPE).build();
    }
}
