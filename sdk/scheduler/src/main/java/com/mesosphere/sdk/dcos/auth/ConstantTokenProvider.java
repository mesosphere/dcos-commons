package com.mesosphere.sdk.dcos.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;

/**
 * Constant Token Provider always returns single pre-configured auth token. It can be used for testing and for known
 * life of configured token.
 */
public class ConstantTokenProvider implements TokenProvider {

    private final DecodedJWT token;

    public ConstantTokenProvider(String token) {
        this.token = JWT.decode(token);
    }

    @Override
    public DecodedJWT getToken() {
       return this.token;
    }

}
