package com.mesosphere.sdk.offer.evaluate;

/**
 * The VisitorResultCollector allows users of a...maybe this shouldn't exist.
 * @param <T> the type of the result
 */
public interface VisitorResultCollector<T> {

    void setResult(T result);

    T getResult();

    /** The empty result, used for visitors that only modify PodSpecs on their way to the next delegate and don't have
     * a return type.
     */
    public static class Empty { }
}
