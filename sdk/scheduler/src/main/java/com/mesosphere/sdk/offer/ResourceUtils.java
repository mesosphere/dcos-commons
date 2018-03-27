package com.mesosphere.sdk.offer;

import org.apache.mesos.Executor;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.*;

import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class encapsulates common methods for scanning collections of Resources.
 */
public class ResourceUtils {

    /**
     * Returns a list of all the resources associated with one or more tasks, including {@link Executor} resources.
     * The returned resources may contain duplicates if multiple tasks have copies of the same resource.
     */
    public static List<Protos.Resource> getAllResources(Collection<TaskInfo> taskInfos) {
        return taskInfos.stream()
                .map(ResourceUtils::getAllResources)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * Returns a list of all the resources associated with a task, including {@link Executor} resources.
     *
     * @param taskInfo The {@link Protos.TaskInfo} containing the {@link Protos.Resource}.
     * @return a list of {@link Protos.Resource}s.
     */
    public static List<Protos.Resource> getAllResources(Protos.TaskInfo taskInfo) {
        List<Resource> resources = new ArrayList<>();
        // Get all resources from both the task level and the executor level
        resources.addAll(taskInfo.getResourcesList());
        if (taskInfo.hasExecutor()) {
            resources.addAll(taskInfo.getExecutor().getResourcesList());
        }
        return resources;
    }

    /**
     * Returns a list of unique resource IDs associated with {@link Resource}s.
     *
     * @param resources Collection of resources from which to extract the unique resource IDs
     * @return List of unique resource IDs
     */
    public static List<String> getResourceIds(Collection<Resource> resources) {
        return resources.stream()
                .map(ResourceUtils::getResourceId)
                .filter(resourceId -> resourceId.isPresent())
                .map(resourceId -> resourceId.get())
                .distinct()
                .collect(Collectors.toList());
    }

    public static String getRole(Resource resource) {
        return new MesosResource(resource).getRole();
    }

    public static Optional<String> getPrincipal(Resource resource) {
        Optional<Resource.ReservationInfo> reservationInfo = getReservation(resource);

        if (reservationInfo.isPresent()) {
            return Optional.of(reservationInfo.get().getPrincipal());
        } else {
            return Optional.empty();
        }
    }

    public static Optional<Resource.ReservationInfo> getReservation(Resource resource) {
        if (resource.getReservationsCount() > 0) {
            return getRefinedReservation(resource);
        } else {
            return getLegacyReservation(resource);
        }
    }

    private static Optional<Resource.ReservationInfo> getRefinedReservation(Resource resource) {
        if (resource.getReservationsCount() == 0) {
            return Optional.empty();
        }

        return Optional.of(resource.getReservations(resource.getReservationsCount() - 1));
    }

    private static Optional<Resource.ReservationInfo> getLegacyReservation(Resource resource) {
        if (resource.hasReservation()) {
            return Optional.of(resource.getReservation());
        } else {
            return Optional.empty();
        }
    }

    public static Optional<String> getResourceId(Resource resource) {
        Optional<Resource.ReservationInfo> reservationInfo = getReservation(resource);
        if (!reservationInfo.isPresent()) {
            return Optional.empty();
        }
        return AuxLabelAccess.getResourceId(reservationInfo.get());
    }

    public static boolean hasResourceId(Resource resource) {
        return getResourceId(resource).isPresent();
    }

    public static Optional<String> getResourceNamespace(Resource resource) {
        Optional<Resource.ReservationInfo> reservationInfo = getReservation(resource);
        if (!reservationInfo.isPresent()) {
            return Optional.empty();
        }
        return AuxLabelAccess.getResourceNamespace(reservationInfo.get());
    }

    public static Optional<String> getPersistenceId(Resource resource) {
        if (resource.hasDisk() && resource.getDisk().hasPersistence()) {
            return Optional.of(resource.getDisk().getPersistence().getId());
        }

        return Optional.empty();
    }

    public static Optional<String> getSourceRoot(Resource resource) {
        if (!isMountVolume(resource)) {
            return Optional.empty();
        }

        return Optional.of(resource.getDisk().getSource().getMount().getRoot());
    }

    public static boolean isOwnedByThisFramework(Resource resource, FrameworkInfo frameworkInfo) {
        final Set<String> frameworkRoles = getRoles(frameworkInfo);
        final Set<String> resourceRoles = getRoles(resource);

        final boolean hasResourceId = ResourceUtils.hasResourceId(resource);
        final boolean matchingRoles = frameworkRoles.containsAll(resourceRoles);

        return hasResourceId && matchingRoles;
    }

    @SuppressWarnings("deprecation")
    private static Set<String> getRoles(FrameworkInfo frameworkInfo) {
        Set<String> roles = frameworkInfo.getRolesList().stream().collect(Collectors.toSet());
        if (frameworkInfo.hasRole()) {
            roles.add(frameworkInfo.getRole());
        }

        return roles.stream().filter(role -> !role.equals(Constants.ANY_ROLE)).collect(Collectors.toSet());
    }

    @SuppressWarnings("deprecation")
    private static Set<String> getRoles(Resource resource) {
        Set<Resource.ReservationInfo> reservations =
                new HashSet<>(resource.getReservationsList().stream().collect(Collectors.toSet()));
        if (resource.hasReservation()) {
            reservations.add(resource.getReservation());
        }

        Set<String> roles =
                new HashSet<>(
                        reservations.stream()
                                .filter(
                                        reservationInfo ->
                                                reservationInfo.getType()
                                                        .equals(Resource.ReservationInfo.Type.DYNAMIC))
                                .map(Resource.ReservationInfo::getRole)
                                .collect(Collectors.toSet()));

        if (resource.hasRole()) {
            roles.add(resource.getRole());
        }


        return roles.stream().filter(role -> !role.equals(Constants.ANY_ROLE)).collect(Collectors.toSet());
    }

    private static boolean isMountVolume(Resource resource) {
        return resource.hasDisk()
                && resource.getDisk().hasSource()
                && resource.getDisk().getSource().hasType()
                && resource.getDisk().getSource().getType()
                .equals(Resource.DiskInfo.Source.Type.MOUNT);
    }
}
