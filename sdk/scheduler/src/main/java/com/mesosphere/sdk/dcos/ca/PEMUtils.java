package com.mesosphere.sdk.dcos.ca;

import org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * A {@link PEMUtils} allows to convert X509 certificates from and to PEM format.
 */
public class PEMUtils {

    private PEMUtils() {
        // do not instantiate
    }

    public static String toPEM(X509Certificate certificate) throws IOException {
        StringWriter stringWriter = new StringWriter();
        PemWriter pemWriter = new PemWriter(stringWriter);
        pemWriter.writeObject(new JcaMiscPEMGenerator(certificate));
        pemWriter.flush();

        return stringWriter.toString();
    }

    public static String toPEM(PrivateKey privateKey) throws IOException {
        StringWriter stringWriter = new StringWriter();

        PemWriter pemWriter = new PemWriter(stringWriter);
        pemWriter.writeObject(new JcaMiscPEMGenerator(privateKey));
        pemWriter.flush();

        return stringWriter.toString();
    }

}
