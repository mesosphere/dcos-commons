package com.mesosphere.sdk.dcos;

/**
 * Created by gabriel on 6/12/17.
 */
public class ResourceRefinmentCapabilityContext {
    private final Capabilities originalCapabilities;
    private final ResourceRefinementCapabilities testCapabilities;

    public ResourceRefinmentCapabilityContext(Capabilities originalCapabilities) {
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
