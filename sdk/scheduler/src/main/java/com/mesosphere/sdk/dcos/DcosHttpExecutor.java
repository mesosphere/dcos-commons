package com.mesosphere.sdk.dcos;

import java.io.IOException;

import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * Class which performs some up-front DC/OS-specific environment validation before returning an {@link Executor}
 * instance. Clients which interact with the DC/OS cluster should (only) accept this DC/OS-specific type in order to
 * ensure that the validation is performed.
 */
public class DcosHttpExecutor {
    private final Executor executor;

    /**
     * Validates that the provided client is valid before constructing an {@link Executor} which may be used via
     * {@link #execute(Request)}.
     */
    public DcosHttpExecutor(HttpClientBuilder clientBuilder) {
        this.executor = Executor.newInstance(clientBuilder.build());
    }

    /**
     * Runs the provided request and returns the response.
     */
    public Response execute(Request request) throws IOException {
        return executor.execute(request);
    }
}
