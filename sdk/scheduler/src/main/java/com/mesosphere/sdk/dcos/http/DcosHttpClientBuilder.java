package com.mesosphere.sdk.dcos.http;

import com.mesosphere.sdk.dcos.auth.TokenProvider;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

/**
 * A {@link DcosHttpClientBuilder} is a helper that simplifies common modifications
 * of {@link org.apache.http.client.HttpClient}.
 */
public class DcosHttpClientBuilder extends org.apache.http.impl.client.HttpClientBuilder {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Disable TLS verification on built HTTP client.
     * @return
     */
    public DcosHttpClientBuilder disableTLSVerification() throws NoSuchAlgorithmException {
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }

        }};

        SSLContext sslContext = SSLContext.getInstance("TLS");

        try {
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (KeyManagementException e) {
            logger.error("Failed to init SSL context with custom TrustManager", e);
        }

        this
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .setSSLContext(sslContext);

        return this;
    }

    /**
     * Enable authentication token.
     * @param provider
     * @return
     */
    public DcosHttpClientBuilder setTokenProvider(TokenProvider provider) {
        this.addInterceptorFirst((HttpRequestInterceptor) (request, context) ->
                request.addHeader("Authorization", String.format("token=%s", provider.getToken().getToken())));
        return this;
    }


    /**
     * Set custom logger that will log all requests.
     * @param logger
     * @return
     */
    public DcosHttpClientBuilder setLogger(Logger logger) {
        this.addInterceptorLast((HttpRequestInterceptor) (request, context) ->
                logger.info(request.toString()));
        return this;
    }


    /**
     * Set default request connection timeout.
     * @param connectionTimeout
     * @return
     */
    public DcosHttpClientBuilder setDefaultConnectionTimeout(int connectionTimeout) {
        RequestConfig requestConfig = RequestConfig
                .custom()
                .setConnectionRequestTimeout(connectionTimeout)
                .build();
        this.setDefaultRequestConfig(requestConfig);
        return this;
    }

}
