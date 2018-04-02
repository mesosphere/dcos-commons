package com.mesosphere.sdk.dcos;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.specification.DefaultPlanGenerator;
import org.json.JSONObject;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;

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
 *         "version": "1.7.3",
 *         "dcos-variant": "open"
 *     }
 */
public class DcosVersion {

    private static final Logger LOGGER = LoggingUtils.getLogger(DefaultPlanGenerator.class);

    /**
     * A broken-down representation of a version string's elements.
     */
    public static class Elements {
        private final String version;

        private Elements(String version) {
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

    private static final String VERSION_KEY = "version";
    private static final String VARIANT_KEY = "dcos-variant";
    private static final String DEV_VERSION_SUFFIX = "-dev";

    private final String version;
    private final DcosVariant variant;

    /**
     * We parse dcos-variant if DC/OS provides it. If the value is absent
     * or is not one of [open|enterprise], we default to `UNKNOWN`.
     *
     * NOTE: This should not be used to build security features.
     */
    public enum DcosVariant {
        OPEN("open"),
        ENTERPRISE("enterprise"),
        UNKNOWN("UNKNOWN");

        private final String variant;

        DcosVariant(String variant) {
            this.variant = variant;
        }

        @Override
        public String toString() {
            return variant;
        }

        private static DcosVariant parseVariant(JSONObject jsonObject) {
            DcosVariant variant = DcosVariant.UNKNOWN;
            if (jsonObject.has(VARIANT_KEY)) {
                String variantStr = jsonObject.getString(VARIANT_KEY);

                if (variantStr.equals(DcosVariant.OPEN.toString())) {
                    variant = DcosVariant.OPEN;
                } else if (variantStr.equals(DcosVariant.ENTERPRISE.toString())) {
                    variant = DcosVariant.ENTERPRISE;
                } else {
                    LOGGER.error("Unexpected dcos-variant : {}", variantStr);
                }
            } else {
                LOGGER.info("{} key not found in {}", VARIANT_KEY, jsonObject);
            }
            return variant;
        }
    }

    @VisibleForTesting
    public DcosVersion(String version, DcosVariant variant) {
        this.version = version;
        this.variant = variant;
    }

    public DcosVersion(JSONObject jsonObject) {
        this(jsonObject.getString(DcosVersion.VERSION_KEY), DcosVariant.parseVariant(jsonObject));
    }

    public String getVersion() {
        return version;
    }

    public Elements getElements() {
        return new Elements(version);
    }

    public DcosVariant getDcosVariant() {
        return variant;
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
