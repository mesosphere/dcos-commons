package com.mesosphere.sdk.testing;

/**
 * Utility methods for testing {@link com.mesosphere.sdk.specification.ServiceSpec}s.
 *
 * @see ServiceSpecTestBuilder for more customization
 */
public class ServiceSpecTestUtils {

    private ServiceSpecTestUtils() {
        // do not instantiate
    }

    /**
     * Tests a "svc.yml" Service Specification YAML file.
     *
     * @throws Exception if the test failed
     */
    public static void test() throws Exception {
        new ServiceSpecTestBuilder().test();
    }

    /**
     * Tests the specified Service Specification YAML file.
     *
     * @param specFilePath the path of the Service Specification YAML file, relative to the service's
     *     {@code src/main/dist} directory
     * @throws Exception if the test failed
     */
    public static void test(String specFilePath) throws Exception {
        new ServiceSpecTestBuilder(ServiceRenderUtils.getDistFile(specFilePath)).test();
    }
}
