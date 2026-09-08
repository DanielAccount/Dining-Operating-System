package com.qr_ordering_system.app.identity.domain;

import com.qr_ordering_system.app.common.persistence.AbstractTenantEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
// uk_role_tenant_name (tenant_id, name) -> schema.sql
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role extends AbstractTenantEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToMany
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_role_permission", columnNames = {"role_id", "permission_id"})
    )
    private Set<Permission> permissions = new HashSet<>();
}
