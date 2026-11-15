package com.retail.pim.taxonomy.domain.model;

import com.retail.pim.shared.domain.AggregateRoot;
import com.retail.pim.shared.domain.DomainEvent;
import com.retail.pim.shared.exceptions.DomainException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Category Aggregate Root representing a classification node in the taxonomy tree.
 * Pure Java - no ORM or Spring annotations.
 */
public class CategoryNode extends AggregateRoot<UUID> {

    private final String code;
    private String name;
    private String description;
    private UUID parentId;
    private int level;
    private final Instant createdAt;
    private Instant updatedAt;

    public CategoryNode(UUID id, String code, String name, String description, UUID parentId, int level) {
        super(id != null ? id : UUID.randomUUID());

        if (code == null || code.trim().isEmpty()) {
            throw new DomainException("Category code cannot be empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new DomainException("Category name cannot be empty");
        }
        if (level < 0) {
            throw new DomainException("Category level cannot be negative");
        }

        this.code = code.trim().toUpperCase();
        this.name = name.trim();
        this.description = description;
        this.parentId = parentId;
        this.level = level;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static CategoryNode createRoot(String code, String name, String description) {
        CategoryNode root = new CategoryNode(UUID.randomUUID(), code, name, description, null, 0);
        root.registerEvent(new CategoryCreatedEvent(root.getId(), root.getCode(), root.getName(), null, 0));
        return root;
    }

    public static CategoryNode createChild(String code, String name, String description, UUID parentId, int parentLevel) {
        if (parentId == null) {
            throw new DomainException("Parent ID required for child category node");
        }
        CategoryNode child = new CategoryNode(UUID.randomUUID(), code, name, description, parentId, parentLevel + 1);
        child.registerEvent(new CategoryCreatedEvent(child.getId(), child.getCode(), child.getName(), parentId, child.getLevel()));
        return child;
    }

    public void updateDetails(String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new DomainException("Category name cannot be empty");
        }
        this.name = name.trim();
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void moveTo(UUID newParentId, int newLevel) {
        if (Objects.equals(this.getId(), newParentId)) {
            throw new DomainException("Cannot move a category node under itself");
        }
        UUID oldParent = this.parentId;
        this.parentId = newParentId;
        this.level = newLevel;
        this.updatedAt = Instant.now();

        registerEvent(new CategoryMovedEvent(this.getId(), oldParent, newParentId, newLevel));
    }

    public boolean isRoot() {
        return this.parentId == null;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public UUID getParentId() {
        return parentId;
    }

    public int getLevel() {
        return level;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public record CategoryCreatedEvent(
            UUID categoryId,
            String code,
            String name,
            UUID parentId,
            int level
    ) implements DomainEvent {
        @Override
        public UUID getEventId() { return UUID.randomUUID(); }
        @Override
        public Instant getOccurredOn() { return Instant.now(); }
        @Override
        public String getEventType() { return "CategoryCreated"; }
    }

    public record CategoryMovedEvent(
            UUID categoryId,
            UUID oldParentId,
            UUID newParentId,
            int newLevel
    ) implements DomainEvent {
        @Override
        public UUID getEventId() { return UUID.randomUUID(); }
        @Override
        public Instant getOccurredOn() { return Instant.now(); }
        @Override
        public String getEventType() { return "CategoryMoved"; }
    }
}
