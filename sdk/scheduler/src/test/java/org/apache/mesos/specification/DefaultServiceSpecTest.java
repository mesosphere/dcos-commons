package org.apache.mesos.specification;

import org.apache.mesos.specification.yaml.YAMLServiceSpecFactory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.File;
import java.util.Set;

public class DefaultServiceSpecTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void valid() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("test.yml.mustache").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateSpecFromYAML(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void invalid() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid.yml.mustache").getFile());
        try {
            YAMLServiceSpecFactory
                    .generateSpecFromYAML(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }
}
