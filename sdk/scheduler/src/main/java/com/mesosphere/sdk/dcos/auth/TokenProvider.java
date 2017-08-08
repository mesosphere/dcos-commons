package com.mesosphere.sdk.dcos.auth;

import com.auth0.jwt.interfaces.DecodedJWT;

import java.io.IOException;

/**
 * TokenProvider describes an interface that provides valid DC/OS auth token.
 */
public interface TokenProvider {

    public DecodedJWT getToken() throws IOException;

}
