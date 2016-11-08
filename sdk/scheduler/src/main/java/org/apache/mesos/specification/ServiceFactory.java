package org.apache.mesos.specification;

/**
 * Created by gabriel on 11/7/16.
 */
public interface ServiceFactory {
    Service getService(String spec);
}
