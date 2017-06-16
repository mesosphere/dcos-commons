package com.mesosphere.sdk.dcos;

/**
 * This class allows temporarily setting the global Capabilities object to support PreReservedResources
 * for testing purposes.
 *
 * e.g.
 *
 * ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
 * try {
 *     // Do something requiring the pre-reserved resources
 * } finally {
 *     context.reset();
 * }
 */
public class ResourceRefinementCapabilityContext {
    private final Capabilities originalCapabilities;
    private final ResourceRefinementCapabilities testCapabilities;

    public ResourceRefinementCapabilityContext(Capabilities originalCapabilities) {
        this.originalCapabilities = originalCapabilities;
        this.testCapabilities = new ResourceRefinementCapabilities(originalCapabilities);
        Capabilities.overrideCapabilities(testCapabilities);
    }

    public void reset() {
        Capabilities.overrideCapabilities(originalCapabilities);
    }

    private static class ResourceRefinementCapabilities extends Capabilities {
        public ResourceRefinementCapabilities(Capabilities capabilities) {
            this(capabilities.dcosCluster);
        }

        private ResourceRefinementCapabilities(DcosCluster dcosCluster) {
            super(dcosCluster);
        }

        @Override
        public boolean supportsPreReservedResources() {
            return true;
        }
    }
}
