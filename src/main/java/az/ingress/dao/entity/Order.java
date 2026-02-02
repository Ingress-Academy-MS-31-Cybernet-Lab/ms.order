package az.ingress.dao.entity;


import az.ingress.enums.OrderStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "orders")
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"items", "discounts"})
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class Order {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @GeneratedValue(generator = "uuid-hibernate-generator")
    @GenericGenerator(name = "uuid-hibernate-generator", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id; // UUID String

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private BigDecimal grossAmount;

    @Column
    private BigDecimal shippingAmount;

    @Column
    private BigDecimal totalDiscount;

    @Column(nullable = false)
    private BigDecimal netAmount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(columnDefinition = "TEXT")
    private String failReason;

    @Column
    private String paymentId;

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private OrderAddress shippingAddress;

    @Version
    private Integer version;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDiscount> discounts = new ArrayList<>();
}