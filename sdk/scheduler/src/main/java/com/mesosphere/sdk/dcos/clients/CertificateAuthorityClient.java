package com.mesosphere.sdk.dcos.clients;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.ContentResponseHandler;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.dcos.DcosHttpExecutor;
import com.mesosphere.sdk.offer.evaluate.security.PEMUtils;

/**
 * Represents abstraction over DC/OS Certificate Authority.
 * @see <a href="https://docs.mesosphere.com/1.9/networking/tls-ssl/ca-api/">TLS CA API</a>
 */
public class CertificateAuthorityClient {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private DcosHttpExecutor httpExecutor;
    private CertificateFactory certificateFactory;

    public CertificateAuthorityClient(DcosHttpExecutor httpExecutor) {
        this.httpExecutor = httpExecutor;

        try {
            this.certificateFactory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            logger.error("Failed to create certificate factory", e);
        }
    }

    /**
     * Create a new certificate from CSR by contacting certificate authority.
     *
     * @param csr A PEM encoded CSR as byte array
     */
    public X509Certificate sign(byte[] csr) throws Exception {
        JSONObject data = new JSONObject();
        data.put("certificate_request", new String(csr, StandardCharsets.UTF_8));
        data.put("profile", "");

        data = doPostRequest("sign", data);
        if (!data.getBoolean("success")) {
            throw new Exception(getErrorString(data));
        }

        String certificate = data.getJSONObject("result").getString("certificate");

        return (X509Certificate) certificateFactory.generateCertificate(
                new ByteArrayInputStream(certificate.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Retrieves complete certificate chain including a root CA certificate for given certificate.
     *
     * @param certificate An end-entity X509Certificate
     */
    public Collection<X509Certificate> chainWithRootCert(X509Certificate certificate) throws Exception {
        JSONObject data = new JSONObject();
        data.put("certificate", PEMUtils.toPEM(certificate));

        data = doPostRequest("bundle", data);
        if (!data.getBoolean("success")) {
            throw new Exception(getErrorString(data));
        }

        String bundle = data.getJSONObject("result").getString("bundle");
        ArrayList<X509Certificate> certificates = new ArrayList<>();

        if (bundle.length() > 0) {
            certificates.addAll(
                    certificateFactory.generateCertificates(
                            new ByteArrayInputStream(bundle.getBytes(StandardCharsets.UTF_8)))
                        .stream()
                        .map(cert -> (X509Certificate) cert)
                        .collect(Collectors.toList()));
            // Bundle response includes also submitted certificate which we don't need
            // so remove it.
            certificates.remove(0);
        }

        // Response should come with Root CA certificate which isn't included in 'bundle'
        String rootCACert = data.getJSONObject("result").getString("root");
        if (rootCACert.length() == 0) {
            throw new CertificateException("Failed to retrieve Root CA certificate");
        }

        certificates.add((X509Certificate) certificateFactory.generateCertificate(
                new ByteArrayInputStream(rootCACert.getBytes(StandardCharsets.UTF_8))));

        return certificates;
    }

    private JSONObject doPostRequest(String path, JSONObject data) throws Exception {
        Request request = Request.Post(new URI(DcosConstants.CA_BASE_URI + path))
                .bodyString(data.toString(), ContentType.APPLICATION_JSON);
        Response response = httpExecutor.execute(request);
        HttpResponse httpResponse = response.returnResponse();

        handleResponseStatusLine(httpResponse.getStatusLine(), 200);

        String responseContent = new ContentResponseHandler().handleEntity(httpResponse.getEntity()).asString();
        return new JSONObject(responseContent);
    }

    /**
     * Handle common responses from different API endpoints of DC/OS CA service.
     */
    private static void handleResponseStatusLine(StatusLine statusLine, int okCode) throws Exception {
        if (statusLine.getStatusCode() != okCode) {
            throw new Exception(String.format("%d - error from CA", statusLine.getStatusCode()));
        }
    }

    /**
     * Extracts the error messages from JSON response.
     */
    private static String getErrorString(JSONObject data) {
        return StreamSupport
                .stream(data.getJSONArray("errors").spliterator(), false)
                .map(error -> (JSONObject) error)
                .map(error -> String.format("[%d] %s", error.getInt("code"), error.getString("message")))
                .collect(Collectors.joining("\n"));
    }
}
