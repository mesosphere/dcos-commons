package com.mesosphere.sdk.dcos;

import org.json.JSONObject;

import com.google.common.annotations.VisibleForTesting;

/**
 * This class encapsulates the response from a DC/OS cluster's dcos-metadata/dcos-version.json endpoint.
 * Example response:
 *     HTTP/1.1 200 OK
 *     Accept-Ranges: bytes
 *     Connection: keep-alive
 *     Content-Length: 154
 *     Content-Type: application/json
 *     Date: Thu, 28 Jul 2016 17:57:48 GMT
 *     ETag: "5797a2e7-9a"
 *     Last-Modified: Tue, 26 Jul 2016 17:50:31 GMT
 *     Server: openresty/1.7.10.2

 *     {
 *         "bootstrap-id": "27f0ee12ec563574dd9a6fa93faba1a1be38bde5",
 *         "dcos-image-commit": "3fe4305af8ab9b4c2c3606569f3c0cb5c5437aa3",
 *         "version": "1.7.3"
 *     }
 */
public class DcosVersion {

    /**
     * A broken-down representation of a version string's elements.
     */
    public static class Elements {
        private final String version;

        @VisibleForTesting
        Elements(String version) {
            this.version = version;
        }

        public int getFirstElement() throws NumberFormatException {
            return Integer.parseInt(getVersionElement(version, 0));
        }

        public int getSecondElement() throws NumberFormatException {
            String secondElem = getVersionElement(version, 1);
            // Trim "-dev" suffix if present:
            if (secondElem.endsWith(DEV_VERSION_SUFFIX)) {
                secondElem = secondElem.substring(0, secondElem.length() - DEV_VERSION_SUFFIX.length());
            }
            return Integer.parseInt(secondElem);
        }

        @Override
        public String toString() {
            return version;
        }
    }

    private static final String BOOTSTRAP_ID_KEY = "bootstrap-id";
    private static final String DCOS_IMAGE_COMMIT = "dcos-image-commit";
    private static final String VERSION_KEY = "version";
    private static final String DEV_VERSION_SUFFIX = "-dev";

    private final String bootstrapId;
    private final String dcosImageCommit;
    private final String version;

    DcosVersion(String bootstrapId, String dcosImageCommit, String version) {
        this.bootstrapId = bootstrapId;
        this.dcosImageCommit = dcosImageCommit;
        this.version = version;
    }

    DcosVersion(JSONObject jsonObject) {
        this((String) jsonObject.get(DcosVersion.BOOTSTRAP_ID_KEY),
             (String) jsonObject.get(DcosVersion.DCOS_IMAGE_COMMIT),
             (String) jsonObject.get(DcosVersion.VERSION_KEY));
    }

    public String getBootstrapId() {
        return bootstrapId;
    }

    public String getDcosImageCommit() {
        return dcosImageCommit;
    }

    public String getVersion() {
        return version;
    }

    public Elements getElements() {
        return new Elements(version);
    }

    private static String getVersionElement(String version, int index) {
        String[] elements = version.split("\\.");
        if (elements.length <= index) {
            throw new NumberFormatException(String.format(
                    "Expected at least %d dot-delimited element(s): %s", index, version));
        }
        return elements[index];
    }
}
