package com.mesosphere.sdk.dcos.auth;


import com.auth0.jwt.interfaces.DecodedJWT;
import org.bouncycastle.util.io.pem.PemReader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class ServiceAccountIAMTokenProviderTest {

    private KeyFactory KEY_FACTORY;

    private String TEST_SERVICE_ACCOUNT = "test_service";

    @Before
    public void init() throws NoSuchAlgorithmException {
        KEY_FACTORY = KeyFactory.getInstance("RSA");
    }

    private KeyPair loadRSAKeyPair() throws IOException, InvalidKeySpecException {
        ClassLoader classLoader = getClass().getClassLoader();

        PrivateKey privateKey;
        PublicKey publicKey;

        try (PemReader pemReader = new PemReader(
                new InputStreamReader(new FileInputStream(
                        classLoader.getResource("rsa-private-key.pem").getFile()), "UTF-8"))) {
            privateKey = KEY_FACTORY.generatePrivate(
                    new PKCS8EncodedKeySpec(pemReader.readPemObject().getContent()));
        }

        try (PemReader pemReader = new PemReader(
                new InputStreamReader(new FileInputStream(
                classLoader.getResource("rsa-public-key.pem").getFile()), "UTF-8"))) {
            publicKey = KEY_FACTORY.generatePublic(
                    new X509EncodedKeySpec(pemReader.readPemObject().getContent()));
        }

        return new KeyPair(publicKey, privateKey);
    }

    // Following tests expects DC/OS to be running on 172.17.0.2 IP address and its not ran by default.
    // To get this test passing the bouncer running on 172.17.0.2 needs to be configured with service account
    // with uid: "test_service" and private key found in resources/rsa-private-key.pem
    // TODO(mh): Maybe we could run a bouncer in the docker container?
    @Ignore
    @Test
    public void testGetTokenAgainstRunningBouncer() throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {

        KeyPair keyPair = loadRSAKeyPair();

        ServiceAccountIAMTokenProvider provider = new ServiceAccountIAMTokenProvider.Builder()
                .setIamUrl(new URL("https://172.17.0.2/acs/api/v1/auth/login"))
                .setUid(TEST_SERVICE_ACCOUNT)
                .setPrivateKey((RSAPrivateKey) keyPair.getPrivate())
                .setDisableTLSVerification(true)
                .build();
        DecodedJWT token = provider.getToken();

        Assert.assertTrue(token.getToken().length() > 0);

    }

}
