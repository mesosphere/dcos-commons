package com.mesosphere.sdk.dcos.secrets;

/**
 * General Secrets client exception.
 */
public class SecretsException extends Exception {

    private final String store;
    private final String path;

    public SecretsException(String message, String store, String path) {
        super(message);
        this.store = store;
        this.path = path;
    }

    public SecretsException(String message, Throwable cause, String store, String path) {
        super(message, cause);
        this.store = store;
        this.path = path;
    }

    public String getStore() {
        return store;
    }

    public String getPath() {
        return path;
    }
}
