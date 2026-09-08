1.  Base Entities

@MappedSuperclass
public abstract class AbstractIdentifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "entity_seq")
    @SequenceGenerator(name = "entity_seq", sequenceName = "entity_id_seq", allocationSize = 50)
    private Long id;

    public Long getId() { return id; }
    protected void setId(Long id) { this.id = id; }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        AbstractIdentifiable that = (AbstractIdentifiable) o;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }


/**
 * For immutable, append-only rows (order history, applied modifiers): never updated,
 * never soft-deleted. Deliberately does NOT extend BaseEntity — no @Version, no
 * updatedAt, no @SoftDelete, because these rows are never meant to change after
 * they're written.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
public abstract class AbstractTenantAuditableEntity extends AbstractIdentifiable {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @ManyToOne
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;
}}

@MappedSuperclass
@Getter @Setter
public abstract class AbstractTenantEntity extends BaseEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @ManyToOne
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;
}


@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@SoftDelete(columnName = "is_deleted")
public abstract class BaseEntity extends AbstractIdentifiable {

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



2. Foundation & Security

@Entity
@Table(name = "tenants", indexes = { @Index(name = "idx_tenant_business_name", columnList = "business_name") })
@Getter @Setter
public class Tenant extends BaseEntity {

    @Column(name = "business_name", nullable = false)
    private String businessName;

    private String contactNumber;
    private String address;

    @Column(nullable = false, columnDefinition = "varchar(64) default 'Asia/Yangon'")
    private String timezone = "Asia/Yangon";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32) default 'ACTIVE'")
    private TenantStatus status = TenantStatus.ACTIVE;
}

@Entity
@Table(name = "branches", indexes = { @Index(name = "idx_branch_tenant", columnList = "tenant_id") })
@Getter @Setter
public class Branch extends AbstractTenantEntity {

    @Column(nullable = false)
    private String name;

    private String address;
    private String contactNumber;

    @Column(columnDefinition = "varchar(64)")
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32) default 'ACTIVE'")
    private BranchStatus status = BranchStatus.ACTIVE;
}

@Entity
@Table(name = "branch_settings")
@Getter
@Setter
public class BranchSettings extends AbstractTenantEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "service_charge_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal serviceChargeRate = BigDecimal.ZERO;

    @Column(name = "invoice_prefix")
    private String invoicePrefix;

    @Column(name = "auto_accept_orders", nullable = false)
    private Boolean autoAcceptOrders = true;

    @Column(name = "require_payment_before_kitchen", nullable = false)
    private Boolean requirePaymentBeforeKitchen = false;
}

@Entity
@Table(
    name = "branch_menu_items",
    indexes = {
        @Index(name = "idx_bmi_tenant", columnList = "tenant_id"),
        @Index(name = "idx_bmi_branch", columnList = "branch_id"),
        @Index(name = "idx_bmi_menu_item", columnList = "menu_item_id")
    }
    // uk_bmi_branch_menu_item (branch_id, menu_item_id) -> schema.sql
)
@Getter @Setter
public class BranchMenuItem extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(name = "price_override", precision = 19, scale = 2)
    private BigDecimal priceOverride;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;
}


@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_user_tenant", columnList = "tenant_id"),
        @Index(name = "idx_user_email_lookup", columnList = "email")
    }
    // FIX: email is now the identity used for unauthenticated tenant-resolution at login,
    // so it must be GLOBALLY unique, not just per-tenant — otherwise "login by email"
    // is ambiguous when two tenants happen to have staff with the same address.
    // Enforced as a soft-delete-safe partial unique index in schema.sql.
    // username uniqueness is still scoped per-tenant -> schema.sql
)
@Getter @Setter
public class User extends AbstractTenantEntity {

    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch; // null = tenant-wide access

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_login_at", columnDefinition = "timestamptz")
    private OffsetDateTime lastLoginAt;

    @Column(name = "preferred_language")
    private String preferredLanguage; // Myanamar or English

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_role"))
    private Role role;

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until", columnDefinition = "timestamptz")
    private OffsetDateTime lockedUntil;
}


@Entity
@Table(name = "roles")
// uk_role_tenant_name (tenant_id, name) -> schema.sql
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
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

@Entity
@Table(name = "permissions", indexes = { @Index(name = "idx_permission_code", columnList = "code") })
// permissions are global (extends BaseEntity, not tenant-scoped) — code should be globally unique -> schema.sql
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Permission extends BaseEntity {

    @Column(nullable = false)
    private String code;

    private String description;

    @ManyToMany(mappedBy = "permissions")
    private Set<Role> roles = new HashSet<>();
}


2. The Menu System

@Entity
@Table(name = "categories", indexes = {
    @Index(name = "idx_category_tenant", columnList = "tenant_id"),
    @Index(name = "idx_category_display_order", columnList = "display_order")
})
@Getter @Setter
public class Category extends AbstractTenantEntity {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "localized_names", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> localizedNames = new HashMap<>();

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;
}



@Entity
@Table(name = "menu_items", indexes = {
    @Index(name = "idx_menu_item_category", columnList = "category_id"),
    @Index(name = "idx_menu_item_tenant_active", columnList = "tenant_id, is_active"),
    @Index(name = "idx_menu_item_tenant_stock", columnList = "tenant_id, is_in_stock")
})
@Getter @Setter
public class MenuItem extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "localized_names", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> localizedNames = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "localized_descriptions", columnDefinition = "jsonb")
    private Map<String, String> localizedDescriptions = new HashMap<>();

    @Column(name = "base_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal basePrice;

    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_in_stock", nullable = false)
    private Boolean isInStock = true;

    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<MenuItemModifierGroup> modifierGroups = new ArrayList<>();
}


@Entity
@Table(name = "modifier_groups", indexes = { @Index(name = "idx_modifier_group_tenant", columnList = "tenant_id") })
@Getter @Setter
@Check(constraints = "min_select <= max_select AND (NOT is_required OR min_select >= 1)")
public class ModifierGroup extends AbstractTenantEntity {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "localized_names", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> localizedNames = new HashMap<>();

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = false;

    @Column(name = "min_select", nullable = false)
    private Integer minSelect = 0;

    @Column(name = "max_select", nullable = false)
    private Integer maxSelect = 1;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @OneToMany(mappedBy = "modifierGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ModifierOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "modifierGroup")
    private List<MenuItemModifierGroup> menuItemLinks = new ArrayList<>();
}


@Entity
@Table(name = "modifier_options", indexes = {
    @Index(name = "idx_modifier_option_tenant", columnList = "tenant_id"),
    @Index(name = "idx_modifier_option_group", columnList = "modifier_group_id")
})
@Getter @Setter
@Check(constraints = "extra_price >= 0")
public class ModifierOption extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "modifier_group_id", nullable = false)
    private ModifierGroup modifierGroup;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "localized_names", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> localizedNames = new HashMap<>();

    @Column(name = "extra_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal extraPrice = BigDecimal.ZERO;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;
}


@Entity
@Table(name = "menu_item_modifier_groups", indexes = {
    @Index(name = "idx_mimg_tenant", columnList = "tenant_id"),
    @Index(name = "idx_mimg_menu_item", columnList = "menu_item_id"),
    @Index(name = "idx_mimg_modifier_group", columnList = "modifier_group_id")
}
    // uk_mimg_menu_item_group (menu_item_id, modifier_group_id) -> schema.sql
)
@Getter @Setter
public class MenuItemModifierGroup extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "modifier_group_id", nullable = false)
    private ModifierGroup modifierGroup;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "required_override")
    private Boolean requiredOverride;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}


3. Tables, Sessions & QR Management

@Entity
@Table(name = "restaurant_tables", indexes = {
    @Index(name = "idx_table_branch", columnList = "branch_id")
}
    // uk_table_branch_number (branch_id, table_number) -> schema.sql
    // uk_table_qr_slug (qr_slug) globally unique -> schema.sql
)
@Getter @Setter
public class TableEntity extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "table_number", nullable = false)
    private String tableNumber;

    @Column(name = "qr_slug", nullable = false)
    private String qrSlug;
}


@Entity
@Table(name = "table_sessions", indexes = {
    @Index(name = "idx_table_session_tenant", columnList = "tenant_id"),
    @Index(name = "idx_table_session_table", columnList = "table_id"),
    @Index(name = "idx_table_session_state", columnList = "tenant_id, session_state")
}
    // uk_table_session_code (table_id, session_code) -> schema.sql
)
@Getter @Setter
public class TableSession extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false)
    private TableEntity table;

    @Column(name = "session_code", nullable = false)
    private String sessionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_state", nullable = false, columnDefinition = "varchar(32)")
    private SessionState sessionState = SessionState.CUSTOMER_ORDERING;

    @Column(name = "opened_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime openedAt;

    @Column(name = "closed_at", columnDefinition = "timestamptz")
    private OffsetDateTime closedAt;

    @Transient
    public boolean isLive() {
        return sessionState != SessionState.CLOSED;
    }
}

/**
 * NEW: Reliable channel for "call waiter" / "request bill" / "I'm paying cash" /
 * "I submitted a KBZ/Wave payment, please verify" events. Backed by a real table
 * instead of a fire-and-forget websocket push, so it survives staff device reconnects
 * after a wifi/power blip and gives you a full audit trail of who acknowledged what.
 * Append-only-ish (rows are opened then resolved, never deleted) — audit-style entity.
 */
@Entity
@Table(name = "service_requests", indexes = {
    @Index(name = "idx_service_request_tenant", columnList = "tenant_id"),
    @Index(name = "idx_service_request_session", columnList = "table_session_id"),
    @Index(name = "idx_service_request_status", columnList = "tenant_id, branch_id, status")
})
@Getter @Setter
public class ServiceRequest extends AbstractTenantEntity {   

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_session_id", nullable = false)
    private TableSession tableSession;

     @ManyToOne(fetch = FetchType.LAZY)
     @JoinColumn(name = "order_id")
     private Order order;

     @ManyToOne(fetch = FetchType.LAZY)
     @JoinColumn(name = "payment_id")
     private Payment payment;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private ServiceRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private ServiceRequestStatus status = ServiceRequestStatus.OPEN;

    // NEW: full 3-state trail, not just the final resolve
    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "acknowledged_at", columnDefinition = "timestamptz")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at", columnDefinition = "timestamptz")
    private OffsetDateTime resolvedAt;

}


4. The Order Engine (With Customizations)

@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_order_tenant_date", columnList = "tenant_id, created_at"),
        @Index(name = "idx_order_branch_date", columnList = "branch_id, created_at"),
        @Index(name = "idx_order_session", columnList = "table_session_id"),
        @Index(name = "idx_order_tenant_status", columnList = "tenant_id, status")
    }
    // idempotency-key & invoice-number uniqueness -> schema.sql (partial, soft-delete safe)
)
@Getter @Setter
@Check(constraints =
    "(order_type = 'DINE_IN' AND table_session_id IS NOT NULL) OR " +
    "(order_type = 'TAKEAWAY' AND table_session_id IS NULL)")
@Check(constraints = "subtotal >= 0 AND tax_amount >= 0 AND discount_amount >= 0 AND service_charge >= 0")
@Check(constraints = "final_amount = subtotal - discount_amount + tax_amount + service_charge")
public class Order extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_session_id")
    private TableSession tableSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, columnDefinition = "varchar(32)")
    private OrderType orderType = OrderType.DINE_IN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private OrderSource source = OrderSource.QR_CUSTOMER;

    //If customer QR order: null or system user
    //If waiter order: waiter user id
    //If cashier order: cashier user id
    @Column(name = "placed_by")
     private Long placedBy;

    @Column(name = "table_number_snapshot")
    private String tableNumberSnapshot;

    private String customerNote;

    private String customerPhoneNumber;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private OrderStatus status = OrderStatus.PENDING;

    //If order is cancelled
    @Column(name = "cancelled_at", columnDefinition = "timestamptz")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "service_charge", nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal serviceCharge = BigDecimal.ZERO;

    @Column(name = "final_amount", nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal finalAmount = BigDecimal.ZERO;

   @OneToMany(mappedBy = "order", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
   private List<OrderItem> items = new ArrayList<>();
}


@Entity
@Table(name = "order_items", indexes = {
    @Index(name = "idx_order_item_tenant", columnList = "tenant_id"),
    @Index(name = "idx_order_item_order", columnList = "order_id"),
    @Index(name = "idx_order_item_menu_item", columnList = "menu_item_id"),
    // NEW: KDS query pattern — "give me all PENDING/PREPARING items for this branch, oldest first"
    @Index(
       name = "idx_order_item_branch_status",
       columnList = "tenant_id, branch_id, item_status, created_at"
   )})
@Getter @Setter
public class OrderItem extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

     // NEW — denormalized from order.branch at creation time.
    // Plain FK column, not a managed relationship: this exists purely so the
    // KDS query doesn't have to join through Order to filter by branch.
    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "item_name_snapshot", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> itemNameSnapshot = new HashMap<>();

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "price_at_time_of_order", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceAtTimeOfOrder;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    // NEW: item-level kitchen status. Without this, a multi-item order forces the
    // waiter to wait for the SLOWEST item before anything can be served — a drink
    // sitting next to a burger for 10 minutes because they're on the same "order".
    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, columnDefinition = "varchar(32)")
    private OrderItemStatus itemStatus = OrderItemStatus.PENDING;

    @Column(name = "ready_at", columnDefinition = "timestamptz")
    private OffsetDateTime readyAt;

    @Column(name = "served_at", columnDefinition = "timestamptz")
    private OffsetDateTime servedAt;

    // OrderItemModifier is an immutable audit-style row (AbstractTenantAuditableEntity,
    // no @SoftDelete). With orphanRemoval=true, simply removing an entry from this list
    // in memory + saving would fire a REAL, PERMANENT SQL DELETE on a row that's supposed
    // to be immutable forever. PERSIST/MERGE only = you can add modifiers, you can never
    // silently destroy them.
    @OneToMany(mappedBy = "orderItem", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    private List<OrderItemModifier> selectedModifiers = new ArrayList<>();
}


@Entity
@Table(name = "order_item_modifiers", indexes = {
    @Index(name = "idx_order_modifier_tenant", columnList = "tenant_id"),
    @Index(name = "idx_order_modifier_order_item", columnList = "order_item_id")
})
@Getter @Setter
public class OrderItemModifier extends AbstractTenantAuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modifier_option_id")
    private ModifierOption modifierOption;

    @Column(name = "modifier_name", nullable = false)
    private String modifierName;

    @Column(name = "modifier_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal modifierPrice;
}


@Entity
@Table(name = "order_status_history", indexes = {
    @Index(name = "idx_order_history_tenant", columnList = "tenant_id"),
    @Index(name = "idx_order_history_order", columnList = "order_id")
})
@Getter @Setter
public class OrderStatusHistory extends AbstractTenantAuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private OrderStatus status;

    @Column(columnDefinition = "TEXT")
    private String remarks;
}

5. Payment & Subscription (Myanmar Optimized)


@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_tenant", columnList = "tenant_id"),
        @Index(name = "idx_payment_order", columnList = "order_id"),
        @Index(name = "idx_payment_tenant_status", columnList = "tenant_id, status"),
	@Index(name = "idx_payment_branch_status",columnList = "tenant_id, branch_id, status")
    }
)
@Getter @Setter
@Check(constraints = "amount > 0")
@Check(constraints = "refunded_amount >= 0 AND refunded_amount <= amount")
public class Payment extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private PaymentStatus status;
	
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_at", columnDefinition = "timestamptz")
    private OffsetDateTime verifiedAt;

    @Column(name = "verification_remarks")
    private String verificationRemarks;

    @Column(name = "rejected_reason")
    private String rejectedReason;

    // Uniqueness (per tenant, when present) -> schema.sql. Anti-fraud control for the
    // KBZ Pay / Wave Pay manual-verification flow: stops the same transfer being
    // approved against two different orders.
    @Column(name = "transaction_reference")
    private String transactionReference;

    @Column(name = "transaction_screenshot_url")
    private String transactionScreenshotUrl;

    @Column(name = "paid_at", columnDefinition = "timestamptz")
    private OffsetDateTime paidAt;
}

/**
 * NEW: Per-branch receiving details for a given payment method — the actual KBZPay/
 * WavePay merchant QR image + phone number/account name the customer pays into.
 * Without this there is nowhere to store what the customer actually needs to see at
 * checkout for a manual-verification digital payment.
 */
@Entity
@Table(name = "branch_payment_details", indexes = {
    @Index(name = "idx_bpd_tenant", columnList = "tenant_id"),
    @Index(name = "idx_bpd_branch", columnList = "branch_id")
}
    // uk_bpd_branch_method (branch_id, method) -> schema.sql
)
@Getter @Setter
public class BranchPaymentDetail extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private PaymentMethod method;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "account_phone_number")
    private String accountPhoneNumber;

    @Column(name = "merchant_qr_image_url")
    private String merchantQrImageUrl;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;
}

// Global — not tenant-scoped. This is your product's price list, shared by every tenant.
@Entity
@Table(name = "subscription_plans")
@Getter @Setter
public class SubscriptionPlan extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "monthly_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "max_branches")
    private Integer maxBranches; // null = unlimited

    @Column(name = "max_users")
    private Integer maxUsers; // null = unlimited
}


// Which plan a tenant is on right now. Uniqueness ("one ACTIVE subscription per
// tenant") -> schema.sql. Gate login/feature-access on status + currentPeriodEnd here —
// this is what makes TenantStatus.SUSPENDED / CANCELLED actually mean something.
@Entity
@Table(name = "tenant_subscriptions", indexes = {
    @Index(name = "idx_tenant_subscription_tenant", columnList = "tenant_id")
})
@Getter @Setter
public class TenantSubscription extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "current_period_start", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime currentPeriodStart;

    @Column(name = "current_period_end", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime currentPeriodEnd;
}


// Which of the existing PaymentMethod values THIS tenant accepts at all (tenant-level
// switch). BranchPaymentDetail above is the per-branch receiving info for those methods.
@Entity
@Table(name = "tenant_payment_methods", indexes = {
    @Index(name = "idx_tenant_payment_method_tenant", columnList = "tenant_id")
}
    // uk_tpm_tenant_method (tenant_id, method) -> schema.sql
)
@Getter @Setter
public class TenantPaymentMethod extends AbstractTenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private PaymentMethod method;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;
}



Enums

public enum BranchStatus { ACTIVE, TEMPORARILY_CLOSED, PERMANENTLY_CLOSED }
public enum OrderStatus { PENDING, ACCEPTED, PREPARING, READY, COMPLETED, CANCELLED }
public enum OrderType { DINE_IN, TAKEAWAY }
public enum PaymentMethod { CASH, KBZ_PAY, WAVE_PAY }
public enum PaymentStatus { PENDING, AWAITING_VERIFICATION, PAID, FAILED, CANCELLED, EXPIRED, PARTIALLY_REFUNDED, REFUNDED }
public enum SessionState { CUSTOMER_ORDERING, AWAITING_CLEANUP, CLOSED }
public enum SubscriptionStatus { TRIAL,ACTIVE, PAST_DUE, CANCELLED }
public enum TenantStatus { ACTIVE, SUSPENDED, TRIAL, TRIAL_EXPIRED, CANCELLED }

// NEW
public enum OrderItemStatus { PENDING, PREPARING, READY, SERVED, CANCELLED }
public enum ServiceRequestType { CALL_WAITER, REQUEST_BILL, PAY_CASH, PAY_DIGITAL_SUBMITTED }
public enum ServiceRequestStatus { OPEN, ACKNOWLEDGED, RESOLVED }
public enum OrderSource { QR_CUSTOMER,WAITER,CASHIER }


Schema.sql

-- === Users ===
-- Email is the identity used for unauthenticated tenant-resolution at login -> globally unique
CREATE UNIQUE INDEX uk_user_email ON users (email) WHERE is_deleted = false;
-- Username is unique per tenant
CREATE UNIQUE INDEX uk_user_tenant_username ON users (tenant_id, username) WHERE is_deleted = false;

-- === Roles ===
CREATE UNIQUE INDEX uk_role_tenant_name ON roles (tenant_id, name) WHERE is_deleted = false;

-- === Permissions (global) ===
CREATE UNIQUE INDEX uk_permission_code ON permissions (code) WHERE is_deleted = false;

-- === Tables & QR ===
CREATE UNIQUE INDEX uk_table_branch_number ON restaurant_tables (branch_id, table_number) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_table_qr_slug ON restaurant_tables (qr_slug) WHERE is_deleted = false;

-- === Table Sessions ===
CREATE UNIQUE INDEX uk_table_session_code ON table_sessions (table_id, session_code) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_table_session_open ON table_sessions (table_id) WHERE session_state <> 'CLOSED'AND is_deleted = false;

-- === Menu ===
CREATE UNIQUE INDEX uk_bmi_branch_menu_item ON branch_menu_items (branch_id, menu_item_id) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_mimg_menu_item_group ON menu_item_modifier_groups (menu_item_id, modifier_group_id) WHERE is_deleted = false;

-- === Orders ===
CREATE UNIQUE INDEX uk_order_tenant_idempotency ON orders (tenant_id, idempotency_key) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_order_tenant_invoice ON orders (tenant_id, invoice_number)
    WHERE is_deleted = false AND invoice_number IS NOT NULL;

-- === Payments ===
-- Anti-fraud: same bank transfer can't be approved against two different orders in the same tenant
CREATE UNIQUE INDEX uk_payment_tenant_txn_ref ON payments (tenant_id, transaction_reference)
    WHERE is_deleted = false AND transaction_reference IS NOT NULL;

CREATE UNIQUE INDEX uk_payment_tenant_idempotency
ON payments (tenant_id, idempotency_key)
WHERE is_deleted = false AND idempotency_key IS NOT NULL;

-- === Payments ===
CREATE UNIQUE INDEX uk_service_request_tenant_idempotency
ON service_requests (tenant_id, idempotency_key)
WHERE is_deleted = false AND idempotency_key IS NOT NULL;

-- === Branch Payment Details ===
CREATE UNIQUE INDEX uk_bpd_branch_method ON branch_payment_details (branch_id, method) WHERE is_deleted = false;
CREATE INDEX idx_payment_branch_status ON payments (tenant_id, branch_id, status);

-- === Branch Payment Details ===
CREATE UNIQUE INDEX uk_branch_settings_branch
ON branch_settings (branch_id)
WHERE is_deleted = false;

-- === Tenant Payment Methods ===
CREATE UNIQUE INDEX uk_tpm_tenant_method ON tenant_payment_methods (tenant_id, method) WHERE is_deleted = false;

-- === Tenant Subscriptions ===
-- Exactly one ACTIVE subscription per tenant at a time (history rows with other statuses are fine)
CREATE UNIQUE INDEX uk_tenant_subscription_active ON tenant_subscriptions (tenant_id)
    WHERE is_deleted = false AND status = 'ACTIVE';