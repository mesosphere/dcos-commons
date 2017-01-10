package com.mesosphere.sdk.dcos;

import org.apache.http.client.fluent.Request;
import org.json.JSONObject;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Instances of this class represent DC/OS clusters.
 */
public class DcosCluster {
    @VisibleForTesting
    static final String DCOS_VERSION_PATH = "/dcos-metadata/dcos-version.json";

    private final URI dcosUri;
    private Optional<DcosVersion> dcosVersion = Optional.empty();

    DcosCluster(URI dcosUri) {
        this.dcosUri = dcosUri;
    }

    public DcosCluster() {
        this(getUriUnchecked(DcosConstants.MESOS_MASTER_URI));
    }

    public URI getDcosUri() {
        return dcosUri;
    }

    public DcosVersion getDcosVersion() throws IOException {
        if (!dcosVersion.isPresent()) {
            dcosVersion = Optional.of(new DcosVersion(new JSONObject(fetchUri(dcosUri + DCOS_VERSION_PATH))));
        }
        return dcosVersion.get();
    }

    /**
     * Broken out into a separate function to allow stubbing out in tests.
     */
    @VisibleForTesting
    protected String fetchUri(String path) throws IOException {
        URI versionUri = getUriUnchecked(path);
        return Request.Get(versionUri).execute().returnContent().toString();
    }

    /**
     * Wrapper around {@link URI} constructor which converts the checked exception to an unchecked
     * exception. Meant for use by static, known-good URLs.
     */
    private static URI getUriUnchecked(String path) {
        try {
            return new URI(path);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Unable to parse internal URL: " + path, e);
        }
    }
}
