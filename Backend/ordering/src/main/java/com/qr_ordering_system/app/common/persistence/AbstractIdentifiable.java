package com.qr_ordering_system.app.common.persistence;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.SequenceGenerator;
import org.hibernate.Hibernate;

@MappedSuperclass
public abstract class AbstractIdentifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "entity_seq")
    @SequenceGenerator(
            name = "entity_seq",
            sequenceName = "entity_id_seq",
            allocationSize = 50
    )
    private Long id;

    public Long getId() {
        return id;
    }

    protected void setId(Long id) {
        this.id = id;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }

        AbstractIdentifiable that = (AbstractIdentifiable) o;

        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
