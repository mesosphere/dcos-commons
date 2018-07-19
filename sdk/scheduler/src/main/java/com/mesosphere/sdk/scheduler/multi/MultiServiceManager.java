package com.mesosphere.sdk.scheduler.multi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.state.CycleDetectingLockUtils;

/**
 * Handles adding/removing active services in a multi-service scheduler, and functions as the central source of truth
 * for running services, including services which are in the process of uninstalling.
 */
public class MultiServiceManager {

    private static final Logger LOGGER = LoggingUtils.getLogger(MultiServiceManager.class);

    /**
     * We use {@link CycleDetectingLockUtils} to throw immediately if a deadlock is detected.
     *
     * This is useful here because the Mesos client implementation that we currently use has "optimizations" that can
     * lead to double locks if we aren't careful:
     * <ul><li>Within a MultiServiceManager lock, we do something that requests more offers (Revive) or more task
     * statuses (Reconcile)</li>
     * <li>The Mesos client gets this request and decides to 'optimize' by immediately returning a cached value in the
     * same thread, e.g. Offers or a TaskStatus</li>
     * <li>That call from the Mesos client reaches back into MultiServiceManager and leads to a double lock</li></ul>
     */
    private final Lock rlock;
    private final Lock rwlock;

    private final Map<String, AbstractScheduler> services = new HashMap<>();
    private final Map<String, String> sanitizedServiceNames = new HashMap<>();

    // Keeps track of whether we've had the registered callback yet.
    // When a client is added, if we're already registered then invoke 'registered()' manually against that client
    private boolean isRegistered;

    public MultiServiceManager(SchedulerConfig schedulerConfig) {
        ReadWriteLock lock = CycleDetectingLockUtils.newLock(schedulerConfig, MultiServiceManager.class);
        this.rlock = lock.readLock();
        this.rwlock = lock.writeLock();
        this.isRegistered = false;
    }

    /**
     * Returns the original names of currently available services.
     */
    public Collection<String> getServiceNames() {
        Collection<String> serviceNames = new TreeSet<>(); // Alphabetical order
        rlock.lock();
        try {
            serviceNames.addAll(services.keySet());
        } finally {
            rlock.unlock();
        }
        return serviceNames;
    }

    /**
     * Returns the specified service by its original name, or an empty {@code Optional} if it's not found.
     */
    public Optional<AbstractScheduler> getService(String serviceName) {
        rlock.lock();
        try {
            return Optional.ofNullable(services.get(serviceName));
        } finally {
            rlock.unlock();
        }
    }

    /**
     * Returns the specified service by its sanitized name, or an empty {@code Optional} if it's not found.
     *
     * A sanitized service name has had its slashes removed. For example,
     * "{@code /path/to/service}" => "{@code path.to.service}". Sanitized names may be used in e.g. URLs that contain
     * the service name.
     */
    public Optional<AbstractScheduler> getServiceSanitized(String sanitizedServiceName) {
        rlock.lock();
        try {
            String originalName = sanitizedServiceNames.get(sanitizedServiceName);
            if (originalName == null) {
                // Unknown sanitized name
                return Optional.empty();
            }
            return Optional.ofNullable(services.get(originalName));
        } finally {
            rlock.unlock();
        }
    }

    /**
     * Adds or updates a running service according to the name in its ServiceSpec. Note: If the service was marked for
     * uninstall via {@link #uninstallService(String)}, it should continue to be added across scheduler restarts in
     * order for uninstall to complete. It should only be omitted after the uninstall callback has been invoked for it.
     *
     * @param service the client to add, or to replace an existing client with if their service names match
     * @return {@code this}
     * @throws IllegalArgumentException if the service name collides with an existing service after reducing slashes to
     *                                  periods
     */
    public MultiServiceManager putService(AbstractScheduler service) {
        String originalName = service.getServiceSpec().getName();
        String sanitizedName = CommonIdUtils.toSanitizedServiceName(originalName);
        rwlock.lock();
        final boolean shouldCallRegistered;
        try {
            // NOTE: If the service is uninstalling, it should already be passed to us as an UninstallScheduler.
            // See SchedulerBuilder.

            // Update the sanitized=>original mapping, and check for a colliding sanitized name,
            // e.g. "/path/to/service" vs "/path/to.service".
            // This differs from an exact match, which we treat as a reconfiguration/update of the prior service.
            String previousOriginalName = sanitizedServiceNames.put(sanitizedName, originalName);
            if (previousOriginalName != null && !originalName.equals(previousOriginalName)) {
                // Undo changes to 'sanitizedServiceNames' before throwing...
                sanitizedServiceNames.put(sanitizedName, previousOriginalName);

                throw new IllegalArgumentException(String.format(
                        "Service named '%s' conflicts with existing service '%s': matching sanitized name '%s'",
                        originalName, previousOriginalName, sanitizedName));
            }

            if (services.put(originalName, service) == null) {
                LOGGER.info("Added new service: {} (now {} service{})",
                        originalName, services.size(), services.size() == 1 ? "" : "s");
            } else {
                LOGGER.info("Replaced existing service: {} (now {} service{})",
                        originalName, services.size(), services.size() == 1 ? "" : "s");
            }

            // To keep things consistent, avoid accessing isRegistered outside of locked code.
            shouldCallRegistered = isRegistered;
        } finally {
            rwlock.unlock();
        }

        if (shouldCallRegistered) {
            // We are already registered. Manually call registered() against this client so that it can initialize.
            // NOTE: We avoid doing this while we are locked. Otherwise we risk a double lock if the registered() call
            // triggers queries against Mesos.
            service.registered(false);
        }

        return this;
    }

    /**
     * Triggers an uninstall for a service, removing it from the list of services when it has finished. Does nothing if
     * the service is already uninstalling or doesn't exist. If the scheduler process is restarted, the service must be
     * added again via {@link #putService(AbstractScheduler)}, at which point it will automatically resume uninstalling.
     *
     * @param serviceName the name of the service to be uninstalled
     */
    public void uninstallService(String serviceName) {
        uninstallServices(Collections.singleton(serviceName));
    }

    /**
     * Returns a service matching the provided {@code TaskStatus}, or an empty {@link Optional} if no match was found.
     */
    public Optional<AbstractScheduler> getMatchingService(Protos.TaskStatus status) {
        Optional<String> sanitizedServiceName;
        try {
            sanitizedServiceName = CommonIdUtils.toSanitizedServiceName(status.getTaskId());
        } catch (TaskException e) {
            sanitizedServiceName = Optional.empty();
        }
        if (!sanitizedServiceName.isPresent()) {
            // Bad task id.
            LOGGER.error("Received task status with malformed id '{}', unable to route to service: {}",
                    status.getTaskId().getValue(), TextFormat.shortDebugString(status));
            return Optional.empty();
        }

        return getServiceSanitized(sanitizedServiceName.get());
    }

    /**
     * UNINSTALL FLOW:
     * 1. uninstallService("foo") is called. This converts the DefaultScheduler for the service to an UninstallScheduler
     * 2. UninstallScheduler internally flags its StateStore with an uninstall bit if one is not already present.
     * 3. The UninstallScheduler proceeds to clean up the service.
     * 4. In the event of a scheduler process restart during cleanup:
     *   a. Upstream builds a new foo using SchedulerBuilder, which internally finds the uninstall bit and returns a
     *      new UninstallScheduler
     *   b. putService(foo) is called with the UninstallScheduler
     *   c. The UninstallScheduler resumes cleanup from where it left off...
     * 5. Sometime after the UninstallScheduler finishes cleanup, it returns FINISHED in response to offers.
     * 6. We remove the service and invoke uninstallCallback.uninstalled(), telling upstream that it's gone. If upstream
     *    invokes putService(foo) again at this point, the service will be relaunched from scratch because the uninstall
     *    bit in ZK will have been cleared.
     */
    public void uninstallServices(Collection<String> finishedServiceNames) {
        // We specifically avoid invoking services while we are locked. The service may make queries against the
        // Mesos client which can lead to a double lock if that then calls back into us.
        Collection<AbstractScheduler> uninstallSchedulersToInitialize = new ArrayList<>();

        rwlock.lock();
        try {
            LOGGER.info("Marking services as uninstalling: {} (out of {} service{})",
                    finishedServiceNames, services.size(), services.size() == 1 ? "" : "s");

            for (String name : finishedServiceNames) {
                AbstractScheduler currentService = services.get(name);
                if (currentService == null) {
                    LOGGER.warn("Service '{}' does not exist, cannot trigger uninstall", name);
                    continue;
                }
                if (currentService instanceof UninstallScheduler) {
                    // Already uninstalling
                    LOGGER.warn("Service '{}' is already uninstalling, leaving as-is", name);
                    continue;
                }

                // Convert the DefaultScheduler to an UninstallScheduler. It will automatically flag itself with an
                // uninstall bit in its state store and then proceed with the uninstall. When the uninstall has
                // completed, it will return FINISHED to its next offers() call, at which point we will remove it. If
                // the scheduler process is restarted before uninstall has completed, the caller should have added it
                // back via putService(). When it's added back, it should be have already been converted to an
                // UninstallScheduler. See SchedulerBuilder.
                AbstractScheduler uninstallScheduler = ((DefaultScheduler) currentService).toUninstallScheduler();
                if (isRegistered) {
                    // We are already registered, so we need to manually do that call for this new service object.
                    // We avoid doing this here, while we are locked.
                    uninstallSchedulersToInitialize.add(uninstallScheduler);
                }
                services.put(name, uninstallScheduler);
            }
        } finally {
            rwlock.unlock();
        }

        // We were already registered, so we need to manually initialize the new UninstallSchedulers with a registered()
        // call. We are careful to avoid calling into the services while we are locked:
        uninstallSchedulersToInitialize.stream().forEach(c -> c.registered(false));
    }

    /**
     * Removes the specified services, following a completed uninstall. Any unknown service names are ignored.
     */
    public void removeServices(Collection<String> uninstalledServiceNames) {
        rwlock.lock();
        try {
            LOGGER.info("Removing {} uninstalled service{}: {} (from {} total service{})",
                    uninstalledServiceNames.size(),
                    uninstalledServiceNames.size() == 1 ? "" : "s",
                    uninstalledServiceNames,
                    services.size(),
                    services.size() == 1 ? "" : "s");

            for (String serviceName : uninstalledServiceNames) {
                services.remove(serviceName);
                sanitizedServiceNames.remove(CommonIdUtils.toSanitizedServiceName(serviceName));
            }
        } finally {
            rwlock.unlock();
        }
    }

    /**
     * Gets a shared/read lock on the underlying data store, and returns all available services.
     * Upstream MUST call {@link #sharedUnlock()} after finishing.
     */
    public Collection<AbstractScheduler> sharedLockAndGetServices() {
        rlock.lock();
        return services.values();
    }

    /**
     * Unlocks a previous lock which was obtained via {@link #sharedLockAndGetServices()}.
     */
    public void sharedUnlock() {
        rlock.unlock();
    }

    /**
     * Notifies underlying services that a registration or re-registration has occurred. After this has been invoked,
     * any services which are added in the future will automatically have their {@code registered()} call invoked to
     * reflect that registration has already occurred, and ensure the invariant that services are registered before
     * receiving offers/statuses.
     */
    public void registered(boolean reRegistered) {
        // We're accomplishing two things here:
        // - Get a snapshot of the current services while within the lock, when we set the isRegistered bit.
        // - Avoid calling into clients while we are locked. They may perform work which could lead to a double lock.
        Collection<AbstractScheduler> currentServices = new ArrayList<>();
        rlock.lock();
        try {
            isRegistered = true;
            LOGGER.info("Notifying {} service{} of {}",
                    services.size(),
                    services.size() == 1 ? "" : "s",
                    reRegistered ? "re-registration" : "initial registration");
            currentServices.addAll(services.values());
        } finally {
            rlock.unlock();
        }
        currentServices.stream().forEach(c -> c.registered(reRegistered));
    }
}
