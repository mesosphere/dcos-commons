package com.mesosphere.sdk.dcos.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

public class ConstantTokenProviderTest {

    private String createToken() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);

        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        Algorithm algorithm = Algorithm.RSA256((
                RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());

        return JWT.create()
                .withExpiresAt(Date.from(Instant.now().plusSeconds(120)))
                .withClaim("uid", "test")
                .sign(algorithm);
    }

    @Test
    public void testToken() throws IOException, NoSuchAlgorithmException {
        String testToken = createToken();
        TokenProvider tokenProvider = new ConstantTokenProvider(testToken);
        Assert.assertEquals(tokenProvider.getToken().getToken(), testToken);
    }

}
