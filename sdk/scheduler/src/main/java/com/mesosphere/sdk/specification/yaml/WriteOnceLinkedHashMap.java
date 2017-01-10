package com.mesosphere.sdk.specification.yaml;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Path;
import javax.validation.metadata.ConstraintDescriptor;
import java.util.HashSet;
import java.util.LinkedHashMap;

/**
 * An extension of LinkedHashMap that supports writing a (key, value) pair only once. It prevents any operation
 * to overwrite existing data.
 *
 * @param <K> Key Type
 * @param <V> Value Type
 */
public class WriteOnceLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
    class DuplicateKeyConstraintViolation implements ConstraintViolation<String> {
        private String key;

        public DuplicateKeyConstraintViolation(String key) {
            this.key = key;
        }

        @Override
        public String getMessage() {
            return "Duplicate key: " + key;
        }

        @Override
        public String getMessageTemplate() {
            return "Duplicate key: " + key;
        }

        @Override
        public String getRootBean() {
            return null;
        }

        @Override
        public Class<String> getRootBeanClass() {
            return null;
        }

        @Override
        public Object getLeafBean() {
            return null;
        }

        @Override
        public Object[] getExecutableParameters() {
            return new Object[0];
        }

        @Override
        public Object getExecutableReturnValue() {
            return null;
        }

        @Override
        public Path getPropertyPath() {
            return null;
        }

        @Override
        public Object getInvalidValue() {
            return null;
        }

        @Override
        public ConstraintDescriptor<?> getConstraintDescriptor() {
            return null;
        }

        @Override
        public <U> U unwrap(Class<U> type) {
            return null;
        }
    }

    @Override
    public V put(K key, V value) {
        if (super.containsKey(key)) {
            HashSet<ConstraintViolation<String>> violations = new HashSet<>();
            violations.add(new DuplicateKeyConstraintViolation(key + ""));
            throw new ConstraintViolationException("Duplicate key: " + key, violations);
        }
        return super.put(key, value);
    }
}
