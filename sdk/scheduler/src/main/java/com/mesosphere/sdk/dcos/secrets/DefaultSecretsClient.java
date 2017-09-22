package com.mesosphere.sdk.dcos.secrets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.dcos.SecretsClient;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.ContentResponseHandler;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Default secrets client.
 */
public class DefaultSecretsClient implements SecretsClient {

    private static final String STORE = "default";
    /**
     * URL path prefix for secret store.
     */
    private static final String BASE_URL = DcosConstants.SECRETS_BASE_URI + String.format("secret/%s/", STORE);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Executor httpExecutor;

    public DefaultSecretsClient(Executor executor) {
        this.httpExecutor = executor;
    }

    @Override
    public Collection<String> list(String path) throws IOException, SecretsException {
        Request httpRequest = Request.Get(urlForPath(String.format("%s?list=true", path)));
        HttpResponse response = httpExecutor.execute(httpRequest).returnResponse();

        handleResponseStatusLine(response.getStatusLine(), 200, path);

        String responseContent = new ContentResponseHandler().handleEntity(response.getEntity()).asString();
        JSONObject data = new JSONObject(responseContent);

        ArrayList<String> secrets = new ArrayList<>();
        data.getJSONArray("array")
                .iterator()
                .forEachRemaining(secret -> secrets.add((String) secret));

        return secrets;
    }

    @Override
    public void create(String path, Secret secret) throws IOException, SecretsException {
        String body = OBJECT_MAPPER.writeValueAsString(secret);
        Request httpRequest = Request.Put(urlForPath(path))
                .bodyString(body, ContentType.APPLICATION_JSON);
        StatusLine statusLine = httpExecutor.execute(httpRequest).returnResponse().getStatusLine();

        handleResponseStatusLine(statusLine, 201, path);
    }

    @Override
    public void delete(String path) throws IOException, SecretsException {
        Request httpRequest = Request.Delete(urlForPath(path));
        StatusLine statusLine = httpExecutor.execute(httpRequest).returnResponse().getStatusLine();
        handleResponseStatusLine(statusLine, 204, path);
    }

    @Override
    public void update(String path, Secret secret) throws IOException, SecretsException {
        String body = OBJECT_MAPPER.writeValueAsString(secret);
        Request httpRequest = Request.Patch(urlForPath(path))
                .bodyString(body, ContentType.APPLICATION_JSON);
        StatusLine statusLine = httpExecutor.execute(httpRequest).returnResponse().getStatusLine();

        handleResponseStatusLine(statusLine, 204, path);
    }

    private static URI urlForPath(String path) {
        try {
            return new URI(BASE_URL + path);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Handle common responses from different API endpoints of DC/OS secrets service.
     */
    private void handleResponseStatusLine(StatusLine statusLine, int okCode, String path) throws SecretsException {
        if (statusLine.getStatusCode() == okCode) {
            return;
        }

        String exceptionMessage = String.format("[%s] %s", statusLine.getStatusCode(), statusLine.getReasonPhrase());

        switch (statusLine.getStatusCode()) {
            case 409:
                throw new SecretsException("Secret already exists: " + exceptionMessage, STORE, path);

            default:
                throw new SecretsException(exceptionMessage, STORE, path);
        }
    }

}
