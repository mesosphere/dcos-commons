package org.apache.mesos.specification;

/**
 * Created by gabriel on 8/31/16.
 */
public class InvalidTaskSpecificationException extends Exception {
    public InvalidTaskSpecificationException(String errMsg) {
        super(errMsg);
    }
}
