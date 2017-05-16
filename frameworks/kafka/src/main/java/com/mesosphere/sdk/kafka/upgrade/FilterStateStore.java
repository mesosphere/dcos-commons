package com.mesosphere.sdk.kafka.upgrade;

import com.mesosphere.sdk.curator.CuratorPersister;
import com.mesosphere.sdk.offer.evaluate.placement.StringMatcher;
import com.mesosphere.sdk.state.DefaultStateStore;
import com.mesosphere.sdk.state.StateStoreException;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * CuratorStateStoreFilter enables to ignore task names that match the filter.
 */
public class FilterStateStore extends DefaultStateStore {
    private  StringMatcher ignoreFilter = null;

    public FilterStateStore(String frameworkName, CuratorPersister persister) {
        super(frameworkName, persister);
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
