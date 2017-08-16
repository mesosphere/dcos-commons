package com.mesosphere.sdk.dcos.secrets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.dcos.SecretsClient;
import com.mesosphere.sdk.dcos.http.URLUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.ContentResponseHandler;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Default secrets client.
 */
public class DefaultSecretsClient implements SecretsClient {

    /**
     * URL path prefix for secret store.
     */
    public static final String STORE_PREFIX = "secret/%s/";

    private URL baseUrl;
    private String store;
    private Executor httpExecutor;

    private final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public DefaultSecretsClient(URL baseUrl, String store, Executor executor) {
        this.baseUrl = URLUtils.addPathUnchecked(baseUrl, String.format(STORE_PREFIX, store));
        this.store = store;
        this.httpExecutor = executor;
    }

    public DefaultSecretsClient(String store, Executor executor) {
        this(URLUtils.fromUnchecked(DcosConstants.SECRETS_BASE_URI), store, executor);
    }

    public DefaultSecretsClient(Executor executor) {
        this("default", executor);
    }

    @Override
    public Collection<String> list(String path) throws IOException, SecretsException {
        URL url = urlForPath(String.format("%s?list=true", path));
        Request httpRequest = Request.Get(url.toString());
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
        Request httpRequest = Request.Put(urlForPath(path).toString())
                .bodyString(body, ContentType.APPLICATION_JSON);
        StatusLine statusLine = httpExecutor.execute(httpRequest).returnResponse().getStatusLine();

        handleResponseStatusLine(statusLine, 201, path);
    }

    @Override
    public void delete(String path) throws IOException, SecretsException {
        Request httpRequest = Request.Delete(urlForPath(path).toString());
        StatusLine statusLine = httpExecutor.execute(httpRequest).returnResponse().getStatusLine();
        handleResponseStatusLine(statusLine, 204, path);
    }

    @Override
    public void update(String path, Secret secret) throws IOException, SecretsException {
        String body = OBJECT_MAPPER.writeValueAsString(secret);
        Request httpRequest = Request.Patch(urlForPath(path).toString())
                .bodyString(body, ContentType.APPLICATION_JSON);
        StatusLine statusLine = httpExecutor.execute(httpRequest).returnResponse().getStatusLine();

        handleResponseStatusLine(statusLine, 204, path);
    }

    @VisibleForTesting
    protected URL urlForPath(String path) {
        return URLUtils.addPathUnchecked(baseUrl, path);
    }

    /**
     * Handle common responses from different API endpoints of DC/OS secrets service.
     * @param statusLine
     * @param okCode
     * @param path
     * @throws SecretsException
     */
    protected void handleResponseStatusLine(StatusLine statusLine, int okCode, String path) throws SecretsException {
        if (statusLine.getStatusCode() == okCode) {
            return;
        }

        String exceptionMessage = String.format("[%s] %s", statusLine.getStatusCode(), statusLine.getReasonPhrase());

        switch (statusLine.getStatusCode()) {
            case 409:
                throw new SecretsException("Secret already exists: " + exceptionMessage, store, path);

            default:
                throw new SecretsException(exceptionMessage, store, path);
        }
    }

}
