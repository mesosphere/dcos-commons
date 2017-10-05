package com.mesosphere.sdk.offer.evaluate.security;

import org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * A {@link PEMUtils} allows to convert X509 certificates from and to PEM format.
 */
public class PEMUtils {

    private PEMUtils() {
        // do not instantiate
    }

    public static byte[] toPEM(PKCS10CertificationRequest csr) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PemWriter pemWriter = new PemWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        try {
            pemWriter.writeObject(new JcaMiscPEMGenerator(csr));
            pemWriter.flush();
        } finally {
            pemWriter.close();
        }
        return os.toByteArray();
    }

    public static String toPEM(X509Certificate certificate) throws IOException {
        StringWriter stringWriter = new StringWriter();
        PemWriter pemWriter = new PemWriter(stringWriter);
        try {
            pemWriter.writeObject(new JcaMiscPEMGenerator(certificate));
            pemWriter.flush();
        } finally {
            pemWriter.close();
        }
        return stringWriter.toString();
    }

    public static String toPEM(PrivateKey privateKey) throws IOException {
        StringWriter stringWriter = new StringWriter();
        PemWriter pemWriter = new PemWriter(stringWriter);
        try {
            pemWriter.writeObject(new JcaMiscPEMGenerator(privateKey));
            pemWriter.flush();
        } finally {
            pemWriter.close();
        }
        return stringWriter.toString();
    }

}
