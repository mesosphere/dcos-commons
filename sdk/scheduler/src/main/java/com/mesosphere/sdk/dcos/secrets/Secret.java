package com.mesosphere.sdk.dcos.secrets;

import java.util.Collection;

/**
 * Secret class represents an HTTP API model for creating and updating a secret.
 */
public class Secret {
    private String value;
    private String author;
    private String created;
    private String description;
    private Collection<String> labels;

    private Secret(String value, String author, String created, String description, Collection<String> labels) {
        this.value = value;
        this.author = author;
        this.created = created;
        this.description = description;
        this.labels = labels;
    }

    private Secret(Builder builder) {
       this(
               builder.value,
               builder.author,
               builder.created,
               builder.description,
               builder.labels
       );
    }

    public String getValue() {
        return value;
    }

    public String getAuthor() {
        return author;
    }

    public String getCreated() {
        return created;
    }

    public String getDescription() {
        return description;
    }

    public Collection<String> getLabels() {
        return labels;
    }

    /**
     * A {@link Secret} class builder.
     */
    public static class Builder {
        private String value;
        private String author;
        private String created;
        private String description;
        private Collection<String> labels;

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder created(String created) {
            this.created = created;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder labels(Collection<String> labels) {
            this.labels = labels;
            return this;
        }

        public Secret build() {
           return new Secret(this);
        }
    }
}
