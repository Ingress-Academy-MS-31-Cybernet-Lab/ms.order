package az.ingress.model.client;

import az.ingress.enums.DiscountScope;
import az.ingress.enums.DiscountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoDto {
    private String code;
    private DiscountScope scope;
    private String targetProductId;
    private DiscountType type;
    private BigDecimal value;
    private BigDecimal minOrderAmount;
}
