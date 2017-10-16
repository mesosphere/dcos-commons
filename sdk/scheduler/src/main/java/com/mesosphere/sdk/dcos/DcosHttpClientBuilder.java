package com.mesosphere.sdk.dcos;

import com.mesosphere.sdk.dcos.auth.TokenProvider;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * A {@link DcosHttpClientBuilder} is a helper that simplifies common modifications
 * of {@link org.apache.http.client.HttpClient}.
 */
public class DcosHttpClientBuilder extends HttpClientBuilder {

    public DcosHttpClientBuilder() {
        super();
    }

    /**
     * Assigns a token provider which will produce HTTP auth tokens to be included in requests.
     *
     * @return this
     */
    public DcosHttpClientBuilder setTokenProvider(TokenProvider provider) {
        this.addInterceptorFirst((HttpRequestInterceptor) (request, context) ->
                request.addHeader("Authorization", String.format("token=%s", provider.getToken().getToken())));
        return this;
    }

    /**
     * Assigns a request connection timeout to be used by requests.
     *
     * @return this
     */
    public DcosHttpClientBuilder setDefaultConnectionTimeout(int connectionTimeout) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(connectionTimeout)
                .build();
        this.setDefaultRequestConfig(requestConfig);
        return this;
    }
}
