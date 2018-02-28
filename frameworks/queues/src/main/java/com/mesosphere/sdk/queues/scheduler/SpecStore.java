package com.mesosphere.sdk.queues.scheduler;

import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.storage.Persister;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Optional;
import java.util.UUID;

/**
 * This class maintains a deduplicated store of all job specifications.
 */
public class SpecStore {
    private static final String SPECS_ROOT_NAME = "Specs";

    private final Persister persister;
    private final ConfigStore<ServiceSpec> configStore;
    private final Object lock = new Object();

    public SpecStore(Persister persister) {
        this.persister = persister;
        this.configStore = new ConfigStore<ServiceSpec>(
                DefaultServiceSpec.getConfigurationFactory(),
                persister,
                SPECS_ROOT_NAME);
    }

    public UUID store(ServiceSpec serviceSpec) throws ConfigStoreException {
        UUID id = getId(serviceSpec);
        if (!configStore.hasKey(id)) {
            configStore.store(id, serviceSpec);
        }

        return id;
    }

    public ServiceSpec fetch(UUID id) throws ConfigStoreException {
        return configStore.fetch(id);
    }

    static UUID getId(ServiceSpec serviceSpec) throws ConfigStoreException {
        byte[] hash = SpecStore.hash(serviceSpec);
        return UUID.nameUUIDFromBytes(hash);
    }

    private static byte[] hash(ServiceSpec serviceSpec) throws ConfigStoreException {
        return DigestUtils.sha1(serviceSpec.toJsonString());
    }
}
