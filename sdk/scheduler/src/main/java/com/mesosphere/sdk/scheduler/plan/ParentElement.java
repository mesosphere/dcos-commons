package com.mesosphere.sdk.scheduler.plan;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * A type of {@link Element} which itself is a collection of child {@link Element}s.
 *
 * @param <C> the type of the child elements
 */
public interface ParentElement<C extends Element> extends Element, Interruptible {
  Logger LOGGER = LoggingUtils.getLogger(ParentElement.class);

  /**
   * Gets the children of this Element.
   */
  List<C> getChildren();

  /**
   * Gets the {@link Strategy} applied to the deployment of this Element's children.
   */
  Strategy<C> getStrategy();

  @Override
  default void interrupt() {
    getStrategy().interrupt();
  }

  @Override
  default void proceed() {
    getStrategy().proceed();
  }

  @Override
  default boolean isInterrupted() {
    return getStrategy().isInterrupted();
  }

  @Override
  default void updateParameters(Map<String, String> parameters) {
    for (C child : getChildren()) {
      child.updateParameters(parameters);
    }
  }

  /**
   * Updates children.
   */
  @Override
  default void update(Protos.TaskStatus taskStatus) {
    Collection<? extends Element> children = getChildren();
    LOGGER.debug(
        "Updated {} with TaskStatus: {}",
        getName(),
        TextFormat.shortDebugString(taskStatus)
    );
    children.forEach(element -> element.update(taskStatus));
  }

  /**
   * Restarts children.
   */
  @Override
  default void restart() {
    Collection<? extends Element> children = getChildren();
    LOGGER.info("Restarting elements within {}: {}", getName(), children);
    children.forEach(element -> element.restart());
  }

  /**
   * Force completes children.
   */
  @Override
  default void forceComplete() {
    Collection<? extends Element> children = getChildren();
    LOGGER.info("Forcing completion of elements within {}: {}", getName(), children);
    children.forEach(element -> element.forceComplete());
  }

  /**
   * Returns all errors from this {@Link Element} and all its children.
   *
   * @param parentErrors Errors from this {@Link Element} itself.
   * @return a combined list of all errors from the parent and all its children.
   */
  default List<String> getErrors(List<String> parentErrors) {
    List<String> errors = new ArrayList<>();
    errors.addAll(parentErrors);
    Collection<? extends Element> children = getChildren();
    children.forEach(element -> errors.addAll(element.getErrors()));
    return errors;
  }

  @Override
  default Status getStatus() {
    Collection<Status> childStatuses = getChildren().stream()
        .map(c -> c.getStatus())
        .collect(Collectors.toList());
    Collection<Status> candidateStatuses =
        getStrategy().getCandidates(getChildren(), Collections.emptyList()).stream()
            .map(cc -> cc.getStatus())
            .collect(Collectors.toList());
    return PlanUtils.getAggregateStatus(
        getName(), childStatuses, candidateStatuses, getErrors(), isInterrupted());
  }
}
