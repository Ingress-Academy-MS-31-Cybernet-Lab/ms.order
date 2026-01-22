package az.ingress.model.client;



import az.ingress.enums.ShippingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private String id;
    private String name;

    // Supplier
    private Long supplierId;        // (Cargo & Payout)
    private String supplierName;

    private BigDecimal price;          // (Customer PRICE)
    private BigDecimal originalPrice;

    private BigDecimal commissionRate; // 0.05 (5%)

    @Builder.Default
    private ShippingType shippingType = ShippingType.STANDARD; // STANDARD vs HEAVY

    private BigDecimal extraShippingCost; // for HEAVY_ITEM
}
