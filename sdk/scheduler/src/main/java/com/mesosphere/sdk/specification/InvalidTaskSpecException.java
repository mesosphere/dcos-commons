package com.mesosphere.sdk.specification;

/**
 * This class is an Exception to be used when unexpected errors are encountered in the handling of
 * TaskSpecifications and TaskTypeSpecifications.
 */
public class InvalidTaskSpecException extends Exception {
    public InvalidTaskSpecException(String errMsg) {
        super(errMsg);
    }
}
