package com.mesosphere.sdk.dcos.ca;

import com.mesosphere.sdk.dcos.CertificateAuthorityClient;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.dcos.http.URLUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.ContentResponseHandler;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A default implementation of {@link CertificateAuthorityClient}.
 */
public class DefaultCAClient implements CertificateAuthorityClient {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private URL baseURL;
    private Executor httpExecutor;
    private CertificateFactory certificateFactory;

    public DefaultCAClient(URL baseURL, Executor executor) {
        this.baseURL = baseURL;
        this.httpExecutor = executor;

        try {
            this.certificateFactory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            logger.error("Failed to create certificate factory", e);
        }
    }

    public DefaultCAClient(Executor executor) {
        this(URLUtils.fromUnchecked(DcosConstants.CA_BASE_URI), executor);
    }

    @Override
    public X509Certificate sign(byte[] csr) throws Exception {
        JSONObject data = new JSONObject();
        data.put("certificate_request", new String(csr, StandardCharsets.UTF_8));
        data.put("profile", "");

        data = doPostRequest("sign", data);
        if (!data.getBoolean("success")) {
            throw new CAException(getErrorString(data));
        }

        String certificate = data.getJSONObject("result").getString("certificate");

        return (X509Certificate) certificateFactory
                .generateCertificate(
                        new ByteArrayInputStream(certificate.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public Collection<X509Certificate> chainWithRootCert(
            X509Certificate certificate) throws Exception {
        JSONObject data = new JSONObject();
        data.put("certificate", PEMUtils.toPEM(certificate));

        data = doPostRequest("bundle", data);
        if (!data.getBoolean("success")) {
            throw new CAException(getErrorString(data));
        }

        String bundle = data.getJSONObject("result").getString("bundle");
        ArrayList<X509Certificate> certificates = new ArrayList<>();

        if (bundle.length() > 0) {
            certificates.addAll(
                    certificateFactory.generateCertificates(
                            new ByteArrayInputStream(bundle.getBytes(StandardCharsets.UTF_8)))
                        .stream()
                        .map(cert -> (X509Certificate) cert)
                        .collect(Collectors.toList())
            );
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
                new ByteArrayInputStream(rootCACert.getBytes(StandardCharsets.UTF_8)))
        );

        return certificates;
    }

    private JSONObject doPostRequest(String path, JSONObject data) throws IOException, CAException {
        Request request = Request.Post(URLUtils.addPathUnchecked(baseURL, path).toString())
                .bodyString(data.toString(), ContentType.APPLICATION_JSON);
        Response response = httpExecutor.execute(request);
        HttpResponse httpResponse = response.returnResponse();

        handleResponseStatusLine(httpResponse.getStatusLine(), 200);

        String responseContent = new ContentResponseHandler().handleEntity(httpResponse.getEntity()).asString();
        return new JSONObject(responseContent);
    }

    /**
     * Handle common responses from different API endpoints of DC/OS CA service.
     * @param statusLine
     * @param okCode
     * @throws CAException
     */
    protected void handleResponseStatusLine(StatusLine statusLine, int okCode) throws CAException {
        if (statusLine.getStatusCode() != okCode) {
            throw new CAException(String.format("%d - error from CA", statusLine.getStatusCode()));
        }
    }

    /**
     * Extracts the error messages from JSON response.
     * @param data
     * @return
     */
    private String getErrorString(JSONObject data) {
        return StreamSupport
                .stream(data.getJSONArray("errors").spliterator(), false)
                .map(error -> (JSONObject) error)
                .map(error -> String.format("[%d] %s", error.getInt("code"), error.getString("message")))
                .collect(Collectors.joining("\n"));
    }

}
