package az.ingress.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "order_items")
@ToString(exclude = "order")
@EqualsAndHashCode(of = "id")
public class OrderItem {

    @javax.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "discount_share")
    private BigDecimal discountShare;

    @Column(name = "final_price", nullable = false)
    private BigDecimal finalPrice;

    @Column(name = "commission_rate")
    private BigDecimal commissionRate;

    @Column(name = "commission_amount")
    private BigDecimal commissionAmount;

    @Column(name = "payout_amount", nullable = false)
    private BigDecimal payoutAmount;

    @Column(name = "shipping_fee")
    private BigDecimal shippingFee;

    @Column(name = "shipping_type")
    private String shippingType;
}