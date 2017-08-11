package com.mesosphere.sdk.dcos;

import com.mesosphere.sdk.dcos.secrets.Secret;
import com.mesosphere.sdk.dcos.secrets.SecretsException;

import java.io.IOException;
import java.util.Collection;

/**
 * Client for communicating with DC/OS secret service API.
 * {@see https://docs.mesosphere.com/1.9/security/secrets/secrets-api/#api-reference}
 */
public interface SecretsClient {

    /**
     * List all secrets paths for given path.
     * @param path
     * @return
     * @throws IOException
     * @throws SecretsException
     */
    Collection<String> list(String path) throws IOException, SecretsException;

    /**
     * Create a new secret.
     * @param path path under which should be a secret created
     * @param secret a secret definition
     * @throws IOException
     */
    void create(String path, Secret secret) throws IOException, SecretsException;

    /**
     * Update a secret.
     * @param path path which contains existing secret.
     * @param secret an updated secret definition.
     * @throws IOException
     */
    void update(String path, Secret secret) throws IOException, SecretsException;

    /**
     * Delete an existing secret.
     * @param path path which contains the secret.
     * @throws IOException
     */
    void delete(String path) throws IOException, SecretsException;

}
