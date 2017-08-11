package com.mesosphere.sdk.dcos.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.mesosphere.sdk.dcos.http.DcosHttpClientBuilder;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Date;

/**
 * Provides a token retrieved by `login` operation against IAM service with given service account.
 */
public class ServiceAccountIAMTokenProvider implements TokenProvider {

    private final URL iamUrl;
    private final String uid;
    private final Executor httpExecutor;
    private final Algorithm signatureAlgorithm;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ServiceAccountIAMTokenProvider(
            URL iamUrl,
            String uid,
            Algorithm signatureAlgorithm,
            Executor executor) {
        this.iamUrl = iamUrl;
        this.uid = uid;
        this.httpExecutor = executor;
        this.signatureAlgorithm = signatureAlgorithm;
    }

    private ServiceAccountIAMTokenProvider(Builder builder) throws InvalidKeySpecException, NoSuchAlgorithmException {
        this(
                builder.iamUrl,
                builder.uid,
                builder.buildAlgorithm(),
                builder.buildExecutor()
        );
    }

    @Override
    public DecodedJWT getToken() throws IOException {
        String serviceLoginToken = JWT.create()
                    .withClaim("uid", uid)
                    .withExpiresAt(Date.from(Instant.now().plusSeconds(120)))
                    .sign(signatureAlgorithm);

        JSONObject data = new JSONObject();
        data.put("uid", uid);
        data.put("token", serviceLoginToken);

        Request request = Request.Post(iamUrl.toString())
                .bodyString(data.toString(), ContentType.APPLICATION_JSON);

        Response response = httpExecutor.execute(request);

        JSONObject responseData = new JSONObject(response.returnContent().asString());
        return JWT.decode(responseData.getString("token"));
    }

    /**
     * A {@link ServiceAccountIAMTokenProvider} class builder.
     */
    public static class Builder {
        private URL iamUrl;
        private String uid;
        private RSAPrivateKey privateKey;
        private Algorithm signatureAlgorithm;
        private boolean disableTLSVerification;
        private int connectionTimeout;

        private final Logger logger = LoggerFactory.getLogger(getClass());

        public Builder() {
            this.disableTLSVerification = false;
            this.connectionTimeout = 5;
        }

        public Builder setIamUrl(URL iamUrl) {
            this.iamUrl = iamUrl;
            return this;
        }

        public Builder setUid(String uid) {
            this.uid = uid;
            return this;
        }

        public Builder setPrivateKey(RSAPrivateKey privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        public Builder setDisableTLSVerification(boolean disableTLSVerification) {
            this.disableTLSVerification = disableTLSVerification;
            return this;
        }

        public Builder setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder setSignatureAlgorithm(Algorithm signatureAlgorithm) {
            this.signatureAlgorithm = signatureAlgorithm;
            return this;
        }

        public Executor buildExecutor() throws NoSuchAlgorithmException {
            DcosHttpClientBuilder httpClientBuilder = new DcosHttpClientBuilder();

            if (disableTLSVerification) {
                httpClientBuilder.disableTLSVerification();
            }

            if (connectionTimeout > 0) {
                httpClientBuilder.setDefaultConnectionTimeout(connectionTimeout);
            }

            return Executor.newInstance(httpClientBuilder.build());
        }

        public Algorithm buildAlgorithm() throws NoSuchAlgorithmException, InvalidKeySpecException {
            // If signature algorithm was provided use injected algorithm.
            if (signatureAlgorithm != null) {
                return signatureAlgorithm;
            }

            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                    privateKey.getModulus(), privateKey.getPrivateExponent());
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

            return Algorithm.RSA256((RSAPublicKey) publicKey, privateKey);
        }

        public ServiceAccountIAMTokenProvider build() throws InvalidKeySpecException, NoSuchAlgorithmException {
            return new ServiceAccountIAMTokenProvider(this);
        }
    }
}
