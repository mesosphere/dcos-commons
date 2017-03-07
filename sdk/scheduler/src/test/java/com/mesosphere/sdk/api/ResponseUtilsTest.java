package com.mesosphere.sdk.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static com.mesosphere.sdk.api.ResponseUtils.jsonOkResponse;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class ResponseUtilsTest {

    @Test
    public void testArrayFormatting() {
        checkJsonOkResponse("[]", jsonOkResponse(new JSONArray()));
        checkJsonOkResponse("[]", jsonOkResponse(new JSONArray(Collections.emptyList())));
        checkJsonOkResponse("[\"hello\"]", jsonOkResponse(new JSONArray(Arrays.asList("hello"))));
        checkJsonOkResponse("[\n  \"hello\",\n  \"hi\"\n]", jsonOkResponse(new JSONArray(Arrays.asList("hello", "hi"))));
    }

    @Test
    public void testObjectFormatting() {
        JSONObject obj = new JSONObject();
        checkJsonOkResponse("{}", jsonOkResponse(obj));
        obj.put("hello", "hi");
        checkJsonOkResponse("{\"hello\": \"hi\"}", jsonOkResponse(obj));
        obj.append("hey", "hello");
        checkJsonOkResponse("{\n  \"hello\": \"hi\",\n  \"hey\": [\"hello\"]\n}", jsonOkResponse(obj));
        obj.append("hey", "hey");
        checkJsonOkResponse("{\n  \"hello\": \"hi\",\n  \"hey\": [\n    \"hello\",\n    \"hey\"\n  ]\n}",
                jsonOkResponse(obj));
    }

    private static void checkJsonOkResponse(String expectedContent, Response r) {
        assertEquals(200, r.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, r.getMediaType());
        assertEquals(expectedContent, r.getEntity().toString());
    }
}
