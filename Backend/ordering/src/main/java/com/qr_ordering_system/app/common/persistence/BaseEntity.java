package com.qr_ordering_system.app.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@SoftDelete(columnName = "is_deleted")
public class BaseEntity extends AbstractIdentifiable{

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;
}
