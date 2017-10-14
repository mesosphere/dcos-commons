package com.mesosphere.sdk.dcos.clients;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.dcos.DcosHttpExecutor;
import com.mesosphere.sdk.dcos.auth.TokenProvider;

import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.json.JSONObject;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.util.Date;

/**
 * Provides a token retrieved by `login` operation against IAM service with given service account.
 */
public class ServiceAccountIAMTokenClient implements TokenProvider {

    private final DcosHttpExecutor httpExecutor;
    private final String uid;
    private final Algorithm signatureAlgorithm;

    public ServiceAccountIAMTokenClient(
            DcosHttpExecutor httpExecutor, String uid, Algorithm signatureAlgorithm)
                    throws InvalidKeySpecException, NoSuchAlgorithmException {
        this.httpExecutor = httpExecutor;
        this.uid = uid;
        this.signatureAlgorithm = signatureAlgorithm;
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

        Request request = Request.Post(DcosConstants.IAM_AUTH_URL)
                .bodyString(data.toString(), ContentType.APPLICATION_JSON);
        String responseData = httpExecutor.execute(request).returnContent().asString();

        return JWT.decode(new JSONObject(responseData).getString("token"));
    }
}
