package org.apache.mesos.specification;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.mesos.Protos;
import org.apache.mesos.specification.yaml.RawServiceSpecification;
import org.apache.mesos.specification.yaml.YAMLServiceSpecFactory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultServiceSpecTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void validExhaustive() throws Exception {
        environmentVariables.set("PORT0", "8080");

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        RawServiceSpecification rawServiceSpecification = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(rawServiceSpecification);
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validMinimal() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validSimple() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-simple.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validPortResource() throws Exception {
        environmentVariables.set("PORT0", "8080");

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-multiple-ports.yml").getFile());
        RawServiceSpecification rawServiceSpecification = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(rawServiceSpecification);

        List<ResourceSpecification> portsResources = serviceSpec.getPods().get(0).getTasks().get(0).getResourceSet()
                .getResources()
                .stream()
                .filter(r -> r.getName().equals("ports"))
                .collect(Collectors.toList());

        Assert.assertEquals(1, portsResources.size());

        Protos.Value.Ranges ports = portsResources.get(0).getValue().getRanges();
        Assert.assertEquals(2, ports.getRangeCount());
        Assert.assertEquals(8080, ports.getRange(0).getBegin(), ports.getRange(0).getEnd());
        Assert.assertEquals(8088, ports.getRange(1).getBegin(), ports.getRange(1).getEnd());
    }

    @Test
    public void invalidReplacementFailurePolicy() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-replacement-failure-policy.yml").getFile());
        try {
            YAMLServiceSpecFactory
                    .generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidPodName() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-pod-name.yml").getFile());
        try {
            YAMLServiceSpecFactory
                    .generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        } catch (JsonMappingException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                ConstraintViolationException cause = (ConstraintViolationException) e.getCause();
                Set<ConstraintViolation<?>> constraintViolations = cause.getConstraintViolations();
                Assert.assertTrue(constraintViolations.size() > 0);
            }
        }
    }

    @Test
    public void invalidPodNamePojo() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        try {
            List<PodSpec> pods = defaultServiceSpec.getPods();
            pods.add(pods.get(0));
            DefaultServiceSpec.newBuilder(defaultServiceSpec)
                    .pods(pods)
                    .build();
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidTaskName() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-name.yml").getFile());
        try {
            YAMLServiceSpecFactory
                    .generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        } catch (JsonMappingException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                ConstraintViolationException cause = (ConstraintViolationException) e.getCause();
                Set<ConstraintViolation<?>> constraintViolations = cause.getConstraintViolations();
                Assert.assertTrue(constraintViolations.size() > 0);
            }
        }
    }

    @Test
    public void invalidTaskNamePojo() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        try {
            List<PodSpec> pods = defaultServiceSpec.getPods();
            PodSpec aPod = pods.get(0);
            List<TaskSpec> tasks = aPod.getTasks();
            tasks.add(tasks.get(0));
            DefaultPodSpec.newBuilder(aPod)
                    .tasks(tasks)
                    .build();
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidTaskSpecNoResource() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-resources.yml").getFile());
        try {
            YAMLServiceSpecFactory
                    .generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidResourceSetName() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-resource-set-name.yml").getFile());
        try {
            YAMLServiceSpecFactory
                    .generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        } catch (JsonMappingException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                ConstraintViolationException cause = (ConstraintViolationException) e.getCause();
                Set<ConstraintViolation<?>> constraintViolations = cause.getConstraintViolations();
                Assert.assertTrue(constraintViolations.size() > 0);
            }
        }
    }
}
