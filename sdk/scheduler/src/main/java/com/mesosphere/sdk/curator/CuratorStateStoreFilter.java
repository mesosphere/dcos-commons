package com.mesosphere.sdk.curator;

import com.mesosphere.sdk.offer.evaluate.placement.StringMatcher;
import com.mesosphere.sdk.state.StateStoreException;
import org.apache.curator.RetryPolicy;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Ignore task names that match the filter.
 */
public class CuratorStateStoreFilter  extends CuratorStateStore {
    private  StringMatcher ignoreFilter = null;

    public CuratorStateStoreFilter(String frameworkName, String connectionString) {
        this(frameworkName, connectionString, CuratorUtils.getDefaultRetry(), "", "");
    }

    public CuratorStateStoreFilter(
            String frameworkName,
            String connectionString,
            RetryPolicy retryPolicy) {
        this(frameworkName, connectionString, retryPolicy, "", "");
    }

    public CuratorStateStoreFilter(
            String frameworkName,
            String connectionString,
            String username,
            String password) {
        this(frameworkName, connectionString, CuratorUtils.getDefaultRetry(), username, password);
    }

    public CuratorStateStoreFilter(
            String frameworkName,
            String connectionString,
            RetryPolicy retryPolicy,
            String username,
            String password) {
        super(frameworkName, connectionString, retryPolicy, username, password);
    }

    public void setIgnoreFilter(StringMatcher ignoreFilter){
        this.ignoreFilter = ignoreFilter;
    }

    @Override
    public Collection<String> fetchTaskNames() throws StateStoreException {
        if (ignoreFilter == null) {
            return super.fetchTaskNames();
        }
        return super.fetchTaskNames()
                .stream()
                .filter(name -> !(ignoreFilter.matches(name)))
                .collect(Collectors.toList());
    }

}
