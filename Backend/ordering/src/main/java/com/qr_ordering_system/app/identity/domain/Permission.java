package com.qr_ordering_system.app.identity.domain;

import com.qr_ordering_system.app.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "permissions", indexes = { @Index(name = "idx_permission_code", columnList = "code") })
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Permission extends BaseEntity {

    @Column(nullable = false)
    private String code;

    private String description;

    @ManyToMany(mappedBy = "permissions")
    private Set<Role> roles = new HashSet<>();
}
