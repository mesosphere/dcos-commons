package com.mesosphere.sdk.api;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.protobuf.Message;
import com.googlecode.protobuf.format.JsonFormat;

class ResponseUtils {

    /**
     * Returns a 200 OK response containing the provided {@link JSONArray}.
     */
    static Response jsonOkResponse(JSONArray jsonArray) {
        return jsonOkResponse(jsonArray.toString(2));
    }

    /**
     * Returns a 200 OK response containing the provided {@link JSONObject}.
     */
    static Response jsonOkResponse(JSONObject jsonObject) {
        return jsonOkResponse(jsonObject.toString(2));
    }

    /**
     * Returns a 200 OK response containing a JSON representation of the provided protobuf {@link Message}.
     */
    static Response jsonOkResponse(Message protoMessage) {
        return jsonOkResponse(new JsonFormat().printToString(protoMessage));
    }

    /**
     * Returns a 200 OK response containing the provided JSON {@link String}.
     */
    static Response jsonOkResponse(String string) {
        return Response.ok(string, MediaType.APPLICATION_JSON_TYPE).build();
    }

    /**
     * Returns a 200 OK response containing the provided plaintext {@link String}.
     */
    static Response plainOkResponse(String string) {
        return Response.ok(string, MediaType.TEXT_PLAIN_TYPE).build();
    }
}
