package org.apache.mesos.specification;

/**
 * This class is an Exception to be used when unexpected errors are encountered in the handling of
 * TaskSpecifications and TaskTypeSpecifications.
 */
public class InvalidTaskSpecificationException extends Exception {
    public InvalidTaskSpecificationException(String errMsg) {
        super(errMsg);
    }
}
