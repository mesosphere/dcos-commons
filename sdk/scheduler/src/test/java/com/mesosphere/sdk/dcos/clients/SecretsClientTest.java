package com.mesosphere.sdk.dcos.clients;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.protocol.HttpContext;
import org.json.JSONObject;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.dcos.DcosHttpExecutor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SecretsClientTest {

    private static final SecretsClient.Payload PAYLOAD =
            new SecretsClient.Payload("scheduler-name", "secret-value", "description");

    @Mock private HttpClientBuilder mockHttpClientBuilder;
    @Mock private CloseableHttpClient mockHttpClient;
    @Mock private CloseableHttpResponse mockHttpResponse;
    @Mock private HttpEntity mockHttpEntity;
    @Mock private StatusLine mockStatusLine;

    private SecretsClient client;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mockHttpClientBuilder.build()).thenReturn(mockHttpClient);
        when(mockHttpClient.execute(
                Mockito.any(HttpUriRequest.class), Mockito.any(HttpContext.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(mockHttpResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(200);

        client = new SecretsClient(new DcosHttpExecutor(mockHttpClientBuilder));
    }

    @Test
    public void testListValidResponse() throws Exception {
        String content = "{'array':['one','two']}";
        // Because of how CA client reads entity twice create 2 responses that represent same buffer.
        when(mockHttpEntity.getContent()).thenReturn(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        Collection<String> secrets = client.list("test");
        Assert.assertTrue(secrets.size() == 2);
        Assert.assertTrue(secrets.contains("one"));
        Assert.assertTrue(secrets.contains("two"));
    }

    @Test
    public void testListWithoutPermission() throws Exception {
        thrown.expect(IOException.class);
        thrown.expectMessage("code=403");

        when(mockStatusLine.getStatusCode()).thenReturn(403);
        client.list("test");
    }

    @Test
    public void testListNotFound() throws Exception {
        thrown.expect(IOException.class);
        thrown.expectMessage("code=404");

        when(mockStatusLine.getStatusCode()).thenReturn(404);
        client.list("test");
    }

    @Test
    public void testCreateValidRequest() throws IOException {
        when(mockStatusLine.getStatusCode()).thenReturn(201);

        client.create("scheduler-name/secret-name", PAYLOAD);
        ArgumentCaptor<HttpUriRequest> passedRequest = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(mockHttpClient).execute(passedRequest.capture(), Mockito.any(HttpContext.class));
        HttpUriRequest request = passedRequest.getValue();

        Assert.assertEquals(request.getMethod(), "PUT");
        Assert.assertEquals(request.getURI().getPath(), "/secrets/v1/secret/default/scheduler-name/secret-name");

        Assert.assertTrue(request instanceof HttpEntityEnclosingRequest);
        HttpEntity httpEntity = ((HttpEntityEnclosingRequest)request).getEntity();

        Assert.assertEquals(httpEntity.getContentType().getValue(), ContentType.APPLICATION_JSON.toString());

        ByteArrayOutputStream content = new ByteArrayOutputStream();
        httpEntity.writeTo(content);
        JSONObject jsonObject = new JSONObject(content.toString("UTF-8"));

        Assert.assertEquals(jsonObject.getString("value"), PAYLOAD.getValue());
        Assert.assertEquals(jsonObject.getString("author"), PAYLOAD.getAuthor());
        Assert.assertEquals(jsonObject.getString("description"), PAYLOAD.getDescription());
    }

    @Test(expected = IOException.class)
    public void testCreateWithoutPermission() throws IOException {
        when(mockStatusLine.getStatusCode()).thenReturn(403);
        client.create("scheduler-name/secret-name", PAYLOAD);
    }

    @Test
    public void testCreateOverwriteExistingSecret() throws IOException {
        thrown.expect(IOException.class);
        thrown.expectMessage("code=409");

        when(mockStatusLine.getStatusCode()).thenReturn(409);
        client.create("scheduler-name/secret-name", PAYLOAD);
    }

    @Test
    public void testUpdate() throws IOException {
        when(mockStatusLine.getStatusCode()).thenReturn(204);
        client.update("scheduler-name/secret-name", PAYLOAD);

        ArgumentCaptor<HttpUriRequest> passedRequest = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(mockHttpClient).execute(passedRequest.capture(), Mockito.any(HttpContext.class));
        HttpUriRequest request = passedRequest.getValue();

        Assert.assertEquals(request.getMethod(), "PATCH");
        Assert.assertEquals(request.getURI().getPath(), "/secrets/v1/secret/default/scheduler-name/secret-name");

        Assert.assertTrue(request instanceof HttpEntityEnclosingRequest);
        HttpEntity httpEntity = ((HttpEntityEnclosingRequest)request).getEntity();

        Assert.assertEquals(httpEntity.getContentType().getValue(), ContentType.APPLICATION_JSON.toString());

        ByteArrayOutputStream content = new ByteArrayOutputStream();
        httpEntity.writeTo(content);
        JSONObject jsonObject = new JSONObject(content.toString("UTF-8"));

        Assert.assertEquals(jsonObject.getString("value"), PAYLOAD.getValue());
        Assert.assertEquals(jsonObject.getString("author"), PAYLOAD.getAuthor());
        Assert.assertEquals(jsonObject.getString("description"), PAYLOAD.getDescription());
    }

    @Test(expected = IOException.class)
    public void testUpdateWithoutPermission() throws IOException {
        when(mockStatusLine.getStatusCode()).thenReturn(403);
        client.update("scheduler-name/secret-name", PAYLOAD);
    }

    @Test(expected = IOException.class)
    public void testUpdateNonExistingSecret() throws IOException {
        when(mockStatusLine.getStatusCode()).thenReturn(404);
        client.update("scheduler-name/secret-name", PAYLOAD);
    }

    @Test
    public void testDelete() throws IOException {
        when(mockStatusLine.getStatusCode()).thenReturn(204);
        client.delete("scheduler-name/secret-name");

        ArgumentCaptor<HttpUriRequest> passedRequest = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(mockHttpClient).execute(passedRequest.capture(), Mockito.any(HttpContext.class));
        HttpUriRequest request = passedRequest.getValue();

        Assert.assertEquals(request.getMethod(), "DELETE");
        Assert.assertEquals(request.getURI().getPath(), "/secrets/v1/secret/default/scheduler-name/secret-name");
    }
}
