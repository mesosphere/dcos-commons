package com.mesosphere.sdk.scheduler.plan;

/**
 * Defines the interface for one {@link Phase} within a {@link Plan}. A Phase is an ordered list of
 * one or more {@link Step}s, which each describe a single unit of work.
 * <p>
 * For example, a Step might represent a cluster task that needs to be updated, while the Phase is
 * a list of all of those cluster tasks.
 * <p>
 * See {@link Plan} docs for more background.
 */
public interface Phase extends ParentElement<Step> { }
