package com.mesosphere.sdk.dcos.clients;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.ContentResponseHandler;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.dcos.DcosHttpExecutor;

/**
 * Client for communicating with DC/OS secret service API.
 *
 * @see <a href="https://docs.mesosphere.com/1.9/security/secrets/secrets-api/#api-reference">Secrets API Reference</a>
 */
public class SecretsClient {

    /**
     * The model for data sent to a secrets service when creating or updating a secret.
     */
    public static class Payload {
        private final String author;
        private final String value;
        private final String description;

        public Payload(String author, String value, String description) {
            this.author = author;
            this.value = value;
            this.description = description;
        }

        public String getAuthor() {
            return author;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public boolean equals(Object o) {
            return EqualsBuilder.reflectionEquals(this, o);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }

        @Override
        public String toString() {
            // Refrain from logging the value
            return String.format("author='%s' description='%s' value=%d bytes", author, description, value.length());
        }
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private DcosHttpExecutor httpExecutor;

    public SecretsClient(DcosHttpExecutor httpExecutor) {
        this.httpExecutor = httpExecutor;
    }

    /**
     * List all secrets paths for given path.
     *
     * @param path location to list
     * @return list of nested paths
     * @throws IOException if the list operation failed to complete
     */
    public Collection<String> list(String path) throws IOException {
        Request httpRequest = Request.Get(uriForPath(String.format("%s?list=true", path)));
        String responseContent = new ContentResponseHandler()
                .handleEntity(query("list", path, httpRequest, 200).getEntity())
                .asString();
        JSONObject data = new JSONObject(responseContent);

        ArrayList<String> secrets = new ArrayList<>();
        data.getJSONArray("array")
                .iterator()
                .forEachRemaining(secret -> secrets.add((String) secret));

        return secrets;
    }

    /**
     * Create a new secret.
     *
     * @param path location to create the secret
     * @param secret a secret definition
     * @throws IOException if the create operation failed to complete
     */
    public void create(String path, Payload secret) throws IOException {
        Request httpRequest = Request.Put(uriForPath(path))
                .bodyString(OBJECT_MAPPER.writeValueAsString(secret), ContentType.APPLICATION_JSON);
        query("create", path, httpRequest, 201);
    }

    /**
     * Update a secret.
     *
     * @param path path which contains an existing secret
     * @param secret an updated secret definition
     * @throws IOException if the update failed to complete
     */
    public void update(String path, Payload secret) throws IOException {
        Request httpRequest = Request.Patch(uriForPath(path))
                .bodyString(OBJECT_MAPPER.writeValueAsString(secret), ContentType.APPLICATION_JSON);
        query("update", path, httpRequest, 204);
    }

    /**
     * Delete an existing secret.
     *
     * @param path path which contains the secret
     * @throws IOException if the operation failed
     */
    public void delete(String path) throws IOException {
        query("delete", path, Request.Delete(uriForPath(path)), 204);
    }

    private static URI uriForPath(String path) {
        try {
            return new URI(DcosConstants.DEFAULT_SECRET_STORE_URI + path);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns the resulting {@link HttpResponse} if the provided query resulted in {@code okCode}, or throws an
     * {@link IOException} if it didn't.
     */
    private HttpResponse query(String operation, String path, Request request, int okCode) throws IOException {
        logger.debug("{} {}", operation, path);
        HttpResponse response = httpExecutor.execute(request).returnResponse();
        StatusLine status = response.getStatusLine();
        if (status.getStatusCode() != okCode) {
            throw new IOException(String.format(
                    "Unable to %s secret at '%s': query='%s', code=%s, reason='%s'",
                    operation, path, request.toString(), status.getStatusCode(), status.getReasonPhrase()));
        }
        return response;
    }
}
