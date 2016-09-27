package org.apache.mesos.specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * This implementation of the TaskSpecification is for test purposes.  It allows what would otherwise be bad practices
 * like changing the ResourceSpecifications encapsulated by the TaskSpecification after construction.
 */
public class TestTaskSpecification extends DefaultTaskSpecification {
    private Collection<ResourceSpecification> mutableResources;

    public TestTaskSpecification(TaskSpecification taskSpecification) {
        super(taskSpecification.getName(),
                taskSpecification.getCommand(),
                Collections.emptyList(),
                taskSpecification.getVolumes(),
                taskSpecification.getPlacement());
        this.mutableResources = taskSpecification.getResources();
    }

    @Override
    public Collection<ResourceSpecification> getResources() {
        return mutableResources;
    }

    public void addResource(ResourceSpecification resourceSpecification) {
        mutableResources = new ArrayList<>(mutableResources);
        mutableResources.add(resourceSpecification);
    }
}
