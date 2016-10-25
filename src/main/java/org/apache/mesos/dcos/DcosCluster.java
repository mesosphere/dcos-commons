package org.apache.mesos.dcos;

import org.apache.commons.io.Charsets;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Instances of this class represent DC/OS clusters.
 */
public class DcosCluster {
    private static final Logger LOGGER = LoggerFactory.getLogger(DcosCluster.class);

    /**
     * Location of file on DC/OS cluster that can be used to extract version information.
     */
    public static final String DCOS_VERSION_PATH = "/opt/mesosphere/active/dcos-metadata/etc/dcos-version.json";

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

    /**
     * Gets the version of DC/OS.
     *
     * @return An {@link Optional} composing {@link DcosVersion} object.
     * Can be empty if there was a failure reading the version file.
     * @throws IOException
     */
    public Optional<DcosVersion> getDcosVersion() {
        if (!dcosVersion.isPresent()) {
            try {
                final Path dcosVersionFile = Paths.get(DCOS_VERSION_PATH);

                if (Files.exists(dcosVersionFile)) {
                    final byte[] bytes = Files.readAllBytes(dcosVersionFile);
                    JSONObject jsonObject = new JSONObject(new String(bytes, Charsets.UTF_8));
                    dcosVersion = Optional.of(new DcosVersion(jsonObject));
                }
            } catch (Exception e) {
                LOGGER.error("Error determining the DC/OS version", e);
            }
        }

        return dcosVersion;
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
