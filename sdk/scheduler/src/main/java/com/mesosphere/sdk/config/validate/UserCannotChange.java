package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration validator which validates that a
 * {@link com.mesosphere.sdk.specification.ServiceSpec}'s user cannot change between deployments.
 * <p>
 * This is to prevent change in user-level permissions while accessing files across updates. If a
 * less-privileged user replaces a more-privilege user (eg. "root" -> "nobody"), this is a breaking
 * change as "nobody" will not have access to "root"-owned files in persistent volumes.
 */
public class UserCannotChange implements ConfigValidator<ServiceSpec> {
  private static final String POD_USER_CHANGED_ERROR_MESSAGE =
      "INVALID CONFIGURATION UPDATE. Cannot change existing pod type user from '%s' to '%s'.%n"
          + "Pod type user must remain the same across deployments. "
          // SUPPRESS CHECKSTYLE MultipleStringLiterals
          + "Revert to previous user '%s' to proceed. Current set of configuration updates will NOT"
          // SUPPRESS CHECKSTYLE MultipleStringLiterals
          + " be applied.";

  private static final String SERVICE_USER_CHANGED_ERROR_MESSAGE =
      "INVALID CONFIGURATION UPDATE. Cannot change user of deployed service from '%s' to '%s'.%n"
          + "Revert to previous user '%s' to proceed. Current set of configuration updates will NOT"
          + " be applied.";

  private static final String CONFIG_FIELD = "user";

  private static final String UNKNOWN_USER = "null";

  @Override
  public Collection<ConfigValidationError> validate(
      Optional<ServiceSpec> oldConfig,
      ServiceSpec newConfig)
  {
    if (!oldConfig.isPresent()) {
      return Collections.emptyList();
    }

    List<ConfigValidationError> errors = new ArrayList<>();
    if (oldConfig.get().getUser() != null &&
        !oldConfig.get().getUser().equals(newConfig.getUser()))
    {
      //Add as a fatal error.
      errors.add(ConfigValidationError.transitionError(
          CONFIG_FIELD,
          oldConfig.get().getUser(),
          newConfig.getUser(),
          String.format(SERVICE_USER_CHANGED_ERROR_MESSAGE,
              oldConfig.get().getUser(), newConfig.getUser(), oldConfig.get().getUser()),
          true));
    }

    // We can't rely on the order of pods to test new pods against old ones.
    Map<String, PodSpec> oldPods = new HashMap<>();
    for (PodSpec oldPod : oldConfig.get().getPods()) {
      oldPods.put(oldPod.getType(), oldPod);
    }
    errors.addAll(checkForUserEqualityAmongPods(oldPods, newConfig.getPods()));
    return errors;
  }

  private List<ConfigValidationError> checkForUserEqualityAmongPods(
      Map<String, PodSpec> oldPods, List<PodSpec> newPods)
  {
    List<ConfigValidationError> errors = new ArrayList<>();
    PodSpec oldPod;
    for (PodSpec newPod : newPods) {
      // If a new pod type is introduced, we don't validate how its user is set as we're only
      // concerned about not updating the user for existing pods in this validation.
      if (oldPods.containsKey(newPod.getType())) {
        oldPod = oldPods.get(newPod.getType());
        if (!oldPod.getUser().equals(newPod.getUser())) {
          String oldUser = oldPod.getUser().isPresent() ? oldPod.getUser().get() : UNKNOWN_USER;
          String newUser = newPod.getUser().isPresent() ? newPod.getUser().get() : UNKNOWN_USER;

          //Add as a fatal error.
          errors.add(ConfigValidationError.transitionError(
              CONFIG_FIELD,
              oldUser,
              newUser,
              String.format(POD_USER_CHANGED_ERROR_MESSAGE, oldUser, newUser, oldUser),
              true));
        }
      }
    }
    return errors;
  }
}
