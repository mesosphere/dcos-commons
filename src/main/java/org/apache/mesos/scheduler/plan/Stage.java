package org.apache.mesos.scheduler.plan;

import java.util.List;

/**
 * Defines the interface for one or more {@link Phase}s, along with any errors encountered while
 * processing those Phases. The Stage is a representation of any work that is currently being
 * performed, divided into steps represented by one or more {@link Phases}, and each step divided
 * into one or more {@link Block}s. This structure is a logical abstraction of a multi-phase process
 * for performing upgrades or maintenance on a service.
 * <p>
 * To give a more concrete example, imagine a database which contains both "Data" nodes and "Index"
 * nodes, and which must be upgraded by first rolling out new Data nodes followed by new Index
 * nodes. This upgrade process could be represented as a single Stage with two Phases, where Phase-0
 * is upgrading the Data nodes and Phase-1 is upgrading the Index nodes. Each Phase would then
 * contain a list of Blocks which each reference an individual Data or Index node to be upgraded.
 * If any errors occurred during the rollout, the process would pause and the Stage would contain a
 * list of one or more error messages to be shown to the user,
 */
public interface Stage extends Completable {

    /**
     * Returns a list of all {@link Phase}s contained in this Stage.
     */
    List<? extends Phase> getPhases();

    /**
     * Returns a list of user-visible descriptive error messages which have been encountered while
     * progressing through this Stage. A non-empty response implies that the Stage is in an Error
     * state.
     */
    List<String> getErrors();
}
