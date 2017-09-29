package com.mesosphere.sdk.dcos.clients;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.protocol.HttpContext;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SecretsClientTest {

    private static final SecretsClient.Payload PAYLOAD =
            new SecretsClient.Payload("scheduler-name", "secret-value", "description");

    @Mock private HttpClient httpClient;
    @Mock private HttpResponse httpResponse;
    @Mock private HttpEntity httpEntity;
    @Mock private StatusLine statusLine;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    private SecretsClient createClientWithStatusLine(StatusLine statusLine) throws IOException {
        SecretsClient client = new SecretsClient(Executor.newInstance(httpClient));

        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpClient.execute(
                Mockito.any(HttpUriRequest.class),
                Mockito.any(HttpContext.class))).thenReturn(httpResponse);

        return client;
    }

    private SecretsClient createClientWithJsonContent(String content) throws IOException {
        SecretsClient client = new SecretsClient(Executor.newInstance(httpClient));

        when(statusLine.getStatusCode()).thenReturn(200);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpResponse.getEntity()).thenReturn(httpEntity);
        // Because of how CA client reads entity twice create 2 responses that represent same buffer.
        when(httpEntity.getContent()).thenReturn(
                new ByteArrayInputStream(content.getBytes("UTF-8")),
                new ByteArrayInputStream(content.getBytes("UTF-8")));
        when(httpClient.execute(
                Mockito.any(HttpUriRequest.class),
                Mockito.any(HttpContext.class))).thenReturn(httpResponse);

        return client;
    }

    @Test
    public void testListValidResponse() throws Exception {
        SecretsClient client = createClientWithJsonContent("{'array':['one','two']}");
        Collection<String> secrets = client.list("test");
        Assert.assertTrue(secrets.size() == 2);
        Assert.assertTrue(secrets.contains("one"));
        Assert.assertTrue(secrets.contains("two"));
    }

    @Test
    public void testListWithoutPermission() throws Exception {
        thrown.expect(IOException.class);
        thrown.expectMessage("code=403");

        when(statusLine.getStatusCode()).thenReturn(403);
        SecretsClient client = createClientWithStatusLine(statusLine);
        client.list("test");
    }

    @Test
    public void testListNotFound() throws Exception {
        thrown.expect(IOException.class);
        thrown.expectMessage("code=404");

        when(statusLine.getStatusCode()).thenReturn(404);
        SecretsClient client = createClientWithStatusLine(statusLine);
        client.list("test");
    }

    @Test
    public void testCreateValidRequest() throws IOException {
        when(statusLine.getStatusCode()).thenReturn(201);
        SecretsClient client = createClientWithStatusLine(statusLine);

        client.create("scheduler-name/secret-name", PAYLOAD);

        ArgumentCaptor<HttpUriRequest> passedRequest = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(httpClient).execute(passedRequest.capture(), Mockito.any(HttpContext.class));
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
        when(statusLine.getStatusCode()).thenReturn(403);
        SecretsClient client = createClientWithStatusLine(statusLine);

        client.create("scheduler-name/secret-name", PAYLOAD);
    }

    @Test
    public void testCreateOverwriteExistingSecret() throws IOException {
        thrown.expect(IOException.class);
        thrown.expectMessage("code=409");

        when(statusLine.getStatusCode()).thenReturn(409);
        SecretsClient client = createClientWithStatusLine(statusLine);

        client.create("scheduler-name/secret-name", PAYLOAD);
    }

    @Test
    public void testUpdate() throws IOException {
        when(statusLine.getStatusCode()).thenReturn(204);
        SecretsClient client = createClientWithStatusLine(statusLine);

        client.update("scheduler-name/secret-name", PAYLOAD);

        ArgumentCaptor<HttpUriRequest> passedRequest = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(httpClient).execute(passedRequest.capture(), Mockito.any(HttpContext.class));
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
        when(statusLine.getStatusCode()).thenReturn(403);
        SecretsClient client = createClientWithStatusLine(statusLine);

        client.update("scheduler-name/secret-name", PAYLOAD);
    }

    @Test(expected = IOException.class)
    public void testUpdateNonExistingSecret() throws IOException {
        when(statusLine.getStatusCode()).thenReturn(404);
        SecretsClient client = createClientWithStatusLine(statusLine);

        client.update("scheduler-name/secret-name", PAYLOAD);
    }

    @Test
    public void testDelete() throws IOException {
        when(statusLine.getStatusCode()).thenReturn(204);
        SecretsClient client = createClientWithStatusLine(statusLine);
        client.delete("scheduler-name/secret-name");

        ArgumentCaptor<HttpUriRequest> passedRequest = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(httpClient).execute(passedRequest.capture(), Mockito.any(HttpContext.class));
        HttpUriRequest request = passedRequest.getValue();

        Assert.assertEquals(request.getMethod(), "DELETE");
        Assert.assertEquals(request.getURI().getPath(), "/secrets/v1/secret/default/scheduler-name/secret-name");
    }

}
