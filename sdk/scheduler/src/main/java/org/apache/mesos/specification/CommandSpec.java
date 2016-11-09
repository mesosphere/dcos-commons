package org.apache.mesos.specification;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface CommandSpec {
    String getValue();
    Map<String, String> getEnvironment();
    Optional<String> getUser();
    Collection<URI> getUris();
}
