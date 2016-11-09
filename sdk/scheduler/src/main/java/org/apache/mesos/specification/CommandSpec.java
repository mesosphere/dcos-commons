package org.apache.mesos.specification;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Created by gabriel on 11/8/16.
 */
public interface CommandSpec {
    String getValue();
    Map<String, String> getEnvironment();
    Optional<String> getUser();
    Collection<URI> getUris();
}
