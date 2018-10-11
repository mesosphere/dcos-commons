/**
 * Instrumentation classes for the scheduler.
 *
 * This is currently an experiment intended to find out whether it is possible to easily intercept <b>all</b> calls
 * between the scheduler and Mesos (both ways), and between the scheduler and its state store (ZooKeeper).
 *
 * Once we have this interception point, we can look at the patterns of interaction of scheduler with the above
 * components in a live deployment, and devise some idea on where in these patterns it would make sense to inject
 * faults.
 *
 * The next stage will be to change these classes in such way that instead of (or in addition to) logging, the calls
 * will be blocked, waiting for an explicit go-ahead from the resilience-tester driving a test. Such explicit
 * synchronization will make it possible to inject faults at precise points in the service lifecycle, leading to
 * (hopefully) reliable and repeatable fault-tolerance tests.
 */
package com.mesosphere.sdk.scheduler.instrumentation;