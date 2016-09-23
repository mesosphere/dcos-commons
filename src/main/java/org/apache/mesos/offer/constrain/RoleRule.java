package org.apache.mesos.offer.constrain;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;

/**
 * Requires that the Offer's resources be assigned to a provided role.
 */
public class RoleRule implements PlacementRule {
    private final String role;

    public RoleRule(String role) {
        this.role = role;
    }

    @Override
    public Offer filter(Offer offer) {
        Offer.Builder offerBuilder = offer.toBuilder().clearResources();
        for (Resource resource : offer.getResourcesList()) {
            // include only resources with matching role
            if (resource.getRole().equals(role)) {
                offerBuilder.addResources(resource);
            }
        }
        return offerBuilder.build();
    }

    @Override
    public String toString() {
        return String.format("RoleRule{role=%s}", role);
    }

    /**
     * A generator which returns a {@link RoleRule} for the provided role.
     */
    public static class Generator extends PassthroughGenerator {
        public Generator(String role) {
            super(new RoleRule(role));
        }
    }
}
