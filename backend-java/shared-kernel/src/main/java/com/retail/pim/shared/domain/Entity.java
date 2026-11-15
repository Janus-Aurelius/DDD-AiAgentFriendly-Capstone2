package com.retail.pim.shared.domain;

import java.util.Objects;

/**
 * Base abstract class for DDD Entities.
 * Entities are distinguished by their persistent unique identifier.
 *
 * @param <ID> Identity type
 */
public abstract class Entity<ID> {

    protected ID id;

    protected Entity() {
    }

    protected Entity(ID id) {
        this.id = id;
    }

    public ID getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity<?> entity = (Entity<?>) o;
        return Objects.equals(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
