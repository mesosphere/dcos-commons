package org.apache.mesos.dcos;

import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Instances of this class represent DC/OS clusters.
 */
public class DcosCluster {
    private static final String DCOS_VERSION_PATH = "/dcos-metadata/dcos-version.json";

    private final URI dcosUri;
    private Optional<DcosVersion> dcosVersion = Optional.empty();

    DcosCluster(URI dcosUri) {
        this.dcosUri = dcosUri;
    }

    public DcosCluster() throws URISyntaxException {
        this(new URI(DcosConstants.MESOS_MASTER_URI));
    }

    public URI getDcosUri() {
        return dcosUri;
    }

    public DcosVersion getDcosVersion() throws IOException, URISyntaxException {
        if (!dcosVersion.isPresent()) {
            URI versionUri = new URI(dcosUri + DCOS_VERSION_PATH);
            Content content = Request.Get(versionUri)
                    .execute().returnContent();
            JSONObject jsonObject = new JSONObject(content.toString());
            dcosVersion = Optional.of(new DcosVersion(jsonObject));
        }

        return dcosVersion.get();
    }
}
