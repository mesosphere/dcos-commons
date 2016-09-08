package org.apache.mesos.scheduler.plan;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.*;

/**
 * Default Phase implementation tracks Blocks both by their UUID and by
 * their ordering. It is an immutable class that can be constructed either
 * directly, or using a fluent style builder.
 */
public class DefaultPhase implements Phase {

    /**
     * Fluent style builder class for {@link DefaultPhase}.
     */
    public static final class Builder {

        private UUID id = UUID.randomUUID();
        private String name = "";
        private List<Block> blocks = new ArrayList<>();

        private Builder() {
        }

        public List<Block> getBlocks() {
            return blocks;
        }

        public Builder addBlock(final Block block) {
            if (block != null) {
                this.blocks.add(block);
            }
            return this;
        }

        public Builder removeBlock(final Block block) {
            if (block != null) {
                this.blocks.remove(block);
            }
            return this;
        }

        public Builder addBlocks(Collection<Block> blocks) {
            if (blocks != null) {
                this.blocks.addAll(blocks);
            }
            return this;
        }

        public Builder clearBlocks() {
            this.blocks.clear();
            return this;
        }

        public UUID getId() {
            return id;
        }

        public Builder setId(UUID id) {
            if (id != null) {
                this.id = id;
            }
            return this;
        }

        public String getName() {
            return name;
        }

        public Builder setName(String name) {
            if (name != null) {
                this.name = name;
            }
            return this;
        }

        public DefaultPhase build() {
            return DefaultPhase.create(id, name, blocks);
        }
    }

    /**
     * Static factory method.
     * @param id The unique identifier for the Phase.
     * @param name The name of the Phase.
     * @param blocks The blocks contained in the Phase.
     * @return A new DefaultPhase constructed from id and name and containing
     * blocks.
     */
    public static DefaultPhase create(
            UUID id, String name, Collection<? extends Block> blocks) {
        return new DefaultPhase(id, name, blocks);
    }

    public static Builder builder() {
        return new Builder();
    }

    private final List<Block> blocks;
    private final Map<UUID, Integer> byId;
    private final String name;
    private final UUID id;

    /**
     * Constructs a new {@link DefaultPhase}. Intentionally visible to subclasses.
     *
     * @param id The unique identifier for the Phase.
     * @param name The name of the Phase.
     * @param blocks The blocks contained in the Phase.
     */
    protected DefaultPhase(
            final UUID id,
            final String name,
            final Collection<? extends Block> blocks) {
        this.id = id;
        this.name = name;
        this.blocks = ImmutableList.copyOf(blocks);
        final ImmutableMap.Builder<UUID, Integer> builder =
                ImmutableMap.builder();
        for (int i = 0; i < this.blocks.size(); ++i) {
            builder.put(this.blocks.get(i).getId(), i);
        }
        byId = builder.build();
    }

    @Override
    public List<? extends Block> getBlocks() {
        return blocks;
    }

    @Override
    public Block getBlock(UUID id) {
        if (id == null) {
            return null;
        }
        return byId.containsKey(id) ? blocks.get(byId.get(id)) : null;
    }

    @Override
    public Block getBlock(int index) {
        if (index > blocks.size()) {
            return null;
        } else {
            return blocks.get(index);
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isComplete() {
        for (Block block : blocks) {
            if (!block.isComplete()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getBlocks(), byId, getName(), getId());
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
